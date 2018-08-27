package edu.wcsu.wcsufs.Writers;

import edu.wcsu.wcsufs.FSDataStructures.INode;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class WriteINode
{
  public static boolean write( RandomAccessFile file, INode inode ) throws IOException
  {
    // Write out the file size.
    byte[] data = ByteBuffer.allocate(4).putInt( inode.getFileSize() ).array();
    file.write( data, 0, 4 );
    //offset += 4;

    // Write out the number of links
    data = new byte[ 1 ];
    data[0] = (byte)inode.getNumberOfLinks();
    file.write( data, 0, 1 );
    //offset += 1;

    // Write out the file type
    data[0] = (byte)inode.getType().getType();
    file.write( data, 0, 1 );
    //offset += 1;

    // Write out the access mode
    data[0] = (byte)inode.getAccessMode();
    file.write( data, 0, 1 );
    //offset += 1;

    // Write out the spare byte
    data[0] = 0;
    file.write( data, 0, 1 );
    //offset += 1;

    // Write out the number of allocate blocks
    data = ByteBuffer.allocate(4).putInt( inode.getAllocatedBlocks() ).array();
    file.write( data, 0, 4 );

    // Write out the direct pointers
    for( int directPointer : inode.getDirectPointers() )
    {
      data = ByteBuffer.allocate(4).putInt( directPointer ).array();
      file.write( data, 0, 4 );
      //offset += 4;
    }

    // Write out the indirect pointer
    data = ByteBuffer.allocate(4).putInt( inode.getIndirectPointer() ).array();
    file.write( data, 0, 4 );

    return true;
  }
}
