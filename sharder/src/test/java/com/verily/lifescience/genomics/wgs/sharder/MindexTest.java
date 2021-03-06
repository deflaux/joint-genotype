/*
 * Copyright 2018 Verily Life Sciences LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.verily.lifescience.genomics.wgs.sharder;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MindexTest {

  @Test
  public void testMindex() throws IOException {
    try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
      Path mindexPath = fs.getPath("mindex");
      ByteBuffer nine = ByteBuffer.allocate(8 * 9);
      LongBuffer nineLongs = nine.asLongBuffer();
      for (int i = 0; i < 9; i++) {
        nineLongs.put(10 + i);
      }
      Files.write(mindexPath, nine.array(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);

      Mindex mindex = new Mindex(mindexPath);
      for (int i = 0; i < 9; i++) {
        assertThat(mindex.get(i)).isEqualTo(10 + i);
      }
      // Going through the items in different orders may reveal a bug in the fetching & caching
      // code.
      for (int i = 8; i >= 0; i--) {
        assertThat(mindex.get(i)).isEqualTo(10 + i);
      }
    }
  }
}
