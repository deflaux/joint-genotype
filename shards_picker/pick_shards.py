#!/usr/bin/env python

#
# pick_shards.py will pick split points at evently-spaced genomic locations.
# It uses the input's header to determine the size of each contig.
#
# The format is the following. Each line is a shard.
# Each shard is a tab-separated sequence of intervals.
# Each interval is written as `contig` <TAB> `start pos (included)` <TAB> `end pos (included)`
#
# example usage:
#
# $ python pick_shards.py 20000 ~/mydata/large_sample.g.vcf > shards_file
#

import sys
import re

if len(sys.argv)!=3:
  print "Usage: python pick_shards.py <number of shards> <vcf file>"
  exit(1)

nshards = int(sys.argv[1])

infile = sys.argv[2]

# contig name -> length (actually length-1 since it's pos of last included base)
length={}
contigs = []

for line in open(infile):
  if not line.startswith('#'): break
  if line.startswith('##contig='):
    # parse e.g. ##contig=<ID=HLA-DRB1*15:01:01:01,length=11080>
    match = re.match('##contig=<ID=([^,]*),[ ]?length=([^>]*)>.*', line)
    if not match:
      print 'Error while parsing: %s' % line
      exit(1)
    length[match.group(1)] = int(match.group(2))
    contigs.append(match.group(1))

total_length = sum(length[k] for k in length)
total_length_of_normal = sum(length[k] for k in length if len(k)<=len('chr19'))
#print 'we have %s contigs.' % len(length.keys())
#print '%s total length\n%s in "normal" contigs' % (total_length, total_length_of_normal)
shard_length = total_length / nshards
# how many base pairs we need to put in this shard
left = shard_length
shard_index = 0
total_sofar = 0
s = []
for contig in contigs:
  start = 1
  while True:
    if length[contig]-start+1 < left:
      # adding this contig doesn't fill the shard
      s += ['%s\t%s\t%s' % (contig, start, length[contig])]
      left -= (length[contig] - start + 1)
      total_sofar += (length[contig] - start + 1)
      break
    else:
      # we fill the shard
      s += ['%s\t%s\t%s' % (contig, start, start + left)]
      print('\t'.join(s))
      shard_index += 1
      total_sofar += left
      # use the rest of this contig
      start = start + left + 1
      left = shard_length
      # special case: last shard, make sure to make it large enough to
      # compensate for rounding errors.
      if (shard_index == nshards - 1):
        left = total_length - total_sofar
      s = []
# print last shard as is
if s: print('\t'.join(s))



