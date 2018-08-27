package edu.wcsu.wcsufs.Writers;

import edu.wcsu.wcsufs.FSDataStructures.SuperBlock;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class WriteSuperBlock
{
  // Member data
  public static boolean write( RandomAccessFile file, SuperBlock superBlock ) throws IOException
  {
    // Write out the fields of a super block.  This is always the first item in a file
    boolean success = true;

    // The magic comes first
    byte[] data = ByteBuffer.allocate(4).putInt( superBlock.getMagic() ).array();
    file.write( data, 0, 4 );


    // Write the number of INodes
    data = ByteBuffer.allocate(4).putInt( superBlock.getNumberOfINodes() ).array();
    file.write( data, 0, 4 );


    // Write the number of Data Blocks
    data = ByteBuffer.allocate(4).putInt( superBlock.getNumberOfDataBlocks() ).array();
    file.write( data, 0, 4 );
    //offset += 4;

    // Write the address of the INode Map
    data = ByteBuffer.allocate(4).putInt( superBlock.getAddressOfINodeMap() ).array();
    file.write( data, 0, 4 );
    //offset += 4;

    // Write the address of the Data Block Map
    data = ByteBuffer.allocate(4).putInt( superBlock.getAddressOfDataBlockMap() ).array();
    file.write( data, 0, 4 );
    //offset += 4;

    // Write the address of the first INode
    data = ByteBuffer.allocate(4).putInt( superBlock.getAddressOfFirstInode() ).array();
    file.write( data, 0, 4 );
    //offset += 4;

    // Write the address of the first Data Block
    data = ByteBuffer.allocate(4).putInt( superBlock.getAddressOfFirstDataBlock() ).array();
    file.write( data, 0, 4 );
    //offset += 4;

    // Write the total number of blocks
    data = ByteBuffer.allocate(4).putInt( superBlock.getTotalBlocks() ).array();
    file.write( data, 0, 4 );

    return true;
  }
}
