package com.verily.lifescience.genomics.wgs.mindex;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import htsjdk.tribble.index.Block;
import htsjdk.tribble.index.Index;
import htsjdk.tribble.index.IndexFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.util.List;

/**
 * Generates a mini-index (with just what we need).
 *
 * <p>The position file holds one shard per line. Each shard is a sequence of genomic intervals. The
 * index is a standard g.vcf.idx file, as generated by htslib or htsjdk.
 *
 * <p>Mindexer generates an index that contains, for each shard, a file offset to the beginning of a
 * record before that shard's start position. If the shard isn't present then the offset instead
 * points past the end of file.
 *
 */
public class Mindexer {

  // an offset that is surely past the end of file. Used to indicate missing shards.
  static final long PAST_EOF = 1L << 62;

  /**
   * Generates a mini-index (with just what we need).
   *
   * <p>See the class documentation for context. The command-line arguments are:
   *
   * <ul>
   *   <li>position_list: the name of a file that lists the shards, one per line.
   *   <li>index_file: the name of a standard g.vcf.idx file, as generated by htslib or htsjdk.
   *   <li>shard_count: the target number of shards. In the future can be any divisor of the number
   *       of lines in the position_list, but for now it has to match.
   *   <li>output_file: the name of the mindex output file. Contents will be binary, shard_count
   *       longs corresponding to the file offset for each shard.
   * </ul>
   */
  public static void main(String[] args) throws IOException, ParseException {
    if (args.length < 4) {
      System.out.println("Usage: mindexer <position_list> <index_file> <shard_count> <output_file>");
      System.exit(2);
    }
    String posFile = args[0];
    String indexFile = args[1];
    int nshards = Integer.valueOf(args[2]);
    String outFile = args[3];
    long total = genMindex(posFile, indexFile, nshards, outFile);
    System.out.printf("%s bytes written. Output in %s", total, outFile);
  }

  /** Computes the mindex, write it to the specified file, return file size. */
  public static long genMindex(String posFile, String indexFile, int nshards, String outFile)
      throws IOException, ParseException {

    long total = 0;
    try (SeekableByteChannel outChan =
        Files.newByteChannel(
            Paths.get(outFile),
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE)) {

      ByteBuffer buf = computeMindex(posFile, indexFile, nshards);

      while (buf.hasRemaining()) {
        long written = outChan.write(buf);
        total += written;
      }
    }
    return total;
  }

  // computeMindex is split off so we can test it easily.
  /**
   * Computes the mindex.
   *
   * <p>The input files are opened as normal Java files (so no support for e.g. Google buckets). The
   * expected contents of the files is as described in the class doc, as is the output format.
   *
   * <p>ParseException can be thrown if the position file is invalid.
   */
  @VisibleForTesting
  static ByteBuffer computeMindex(String posFile, String indexFile, int nshards)
      throws IOException, ParseException {
    ImmutableList<Position> positions = Position.fromFile(Paths.get(posFile));
    Preconditions.checkArgument(
        positions.size() == nshards,
        "Expected %s entries in position_list, but got %s",
        nshards,
        positions.size());
    // the mindex contains one long per shard. A long takes up 8 bytes.
    ByteBuffer buf = ByteBuffer.allocate(nshards * 8);
    LongBuffer lbuf = buf.asLongBuffer();
    Index index = IndexFactory.loadIndex(indexFile);
    boolean isOutOfFile = false;

    for (int shard = 0; shard < nshards; shard++) {
      Position pos = positions.get(shard);
      if (!index.containsChromosome(pos.contig())) {
        // that contig isn't even in the index! Output an out-of-file index. This is our convention.
        // No subsequent contig should be present (since contigs are in order).
        lbuf.put(PAST_EOF);
        isOutOfFile = true;
      } else {
        if (isOutOfFile) {
          throw new RuntimeException(
              "There is a gap in the index, this shouldn't happen! Contig: " + pos.contig());
        }
        List<Block> blocksCoveringShard = index.getBlocks(pos.contig(), pos.pos(), pos.pos());
        long offset = blocksCoveringShard.get(0).getStartPosition();
        lbuf.put(offset);
      }
    }
    return buf;
  }
}