package edu.wcsu.wcsufs.Readers;

import edu.wcsu.wcsufs.FSDataStructures.INode;
import edu.wcsu.wcsufs.FSDataStructures.INodeType;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class ReadINode
{
  public static INode read( RandomAccessFile inputFile, int INodeNumber ) throws IOException
  {
    // Allocate a data buffer
    byte[] data = new byte[4];

    // Read in the size
    inputFile.read( data, 0, 4 );
    int fileSize = ByteBuffer.wrap( data ).getInt();

    // Get the number of links
    data = new byte[1];
    inputFile.read( data, 0, 1 );
    int numbeOfLinks = data[0];

    // Get the type
    inputFile.read( data, 0, 1 );
    INodeType type = INodeType.lookUpType( (int)data[0] );

    // Get the access mode
    data = new byte[1];
    inputFile.read( data, 0, 1 );
    int accessMode = data[0];

    // Get the spare byte
    data = new byte[1];
    inputFile.read( data, 0, 1 );
    int spare = data[0];

    // Read in the number of allocated blocks
    data = new byte[4];
    inputFile.read( data, 0, 4 );
    int allocatedBlocks = ByteBuffer.wrap( data ).getInt();

    // Read in the direct pointers
    int[] directPointers = new int[ INode.NUMBER_OF_DIRECT_POINTERS ];

    for( int i = 0; i < INode.NUMBER_OF_DIRECT_POINTERS; i++ )
    {
      inputFile.read( data, 0, 4 );
      directPointers[i] = ByteBuffer.wrap( data ).getInt();
    }

    // Read in the indirect pointer
    inputFile.read( data, 0, 4 );
    int indirectPointer = ByteBuffer.wrap( data ).getInt();

    // Now make up the INode
    INode newINode = new INode( INodeNumber );
    newINode.setFileSize( fileSize );
    newINode.setNumberOfLinks( numbeOfLinks );
    newINode.setType( type );
    newINode.setAccessMode( accessMode );
    newINode.setAllocatedBlocks( allocatedBlocks );
    newINode.setDirectPointers( directPointers );
    newINode.setIndirectPointer( indirectPointer );

    // That's it
    return newINode;

  }

}

