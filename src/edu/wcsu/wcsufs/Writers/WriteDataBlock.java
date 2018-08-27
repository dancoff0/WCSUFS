package edu.wcsu.wcsufs.Writers;

import edu.wcsu.wcsufs.FSDataStructures.DataBlock;

import java.io.IOException;
import java.io.RandomAccessFile;

public class WriteDataBlock
{
  public static boolean write( RandomAccessFile file, DataBlock dataBlock ) throws IOException
  {
    // Get the data from the block and ...
    byte[] data = dataBlock.getData();

    // ... write it out.
    file.write( data, 0, data.length );

    // That's it
    return true;
  }
}
