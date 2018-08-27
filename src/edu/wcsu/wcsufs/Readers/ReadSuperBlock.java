package edu.wcsu.wcsufs.Readers;

import edu.wcsu.wcsufs.Exceptions.IncorrectMagicException;
import edu.wcsu.wcsufs.FSDataStructures.SuperBlock;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class ReadSuperBlock
{
  public static SuperBlock read( RandomAccessFile inputFile ) throws IOException, IncorrectMagicException
  {
    // The super block must be the very first block
    byte[] data = new byte[4];
    inputFile.seek( 0 );
    inputFile.read( data, 0, 4 );
    int magic = ByteBuffer.wrap( data ).getInt();
    inputFile.read( data, 0, 4 );
    int numberOfINodes = ByteBuffer.wrap( data ).getInt();
    inputFile.read( data, 0, 4 );
    int numberOfDataBlocks = ByteBuffer.wrap( data ).getInt();
    inputFile.read( data, 0, 4 );
    int addressOfINodeMap = ByteBuffer.wrap( data ).getInt();
    inputFile.read( data, 0, 4 );
    int addressOfDataBlockMap = ByteBuffer.wrap( data ).getInt();
    inputFile.read( data, 0, 4 );
    int addressOfFirstINode = ByteBuffer.wrap( data ).getInt();
    inputFile.read( data, 0, 4 );
    int addressOfFirstDataBlock = ByteBuffer.wrap( data ).getInt();
    inputFile.read( data, 0, 4 );
    int totalBlocks = ByteBuffer.wrap( data ).getInt();

    // Check the magic
    if( magic != SuperBlock.MAGIC )
    {
      throw new IncorrectMagicException( "This does not appear to be a WCSU file system: the magic is incorrect" );
    }

    SuperBlock superBlock = new SuperBlock();
    superBlock.setMagic( magic );
    superBlock.setNumberOfINodes( numberOfINodes );
    superBlock.setNumberOfDataBlocks( numberOfDataBlocks );
    superBlock.setAddressOfINodeMap( addressOfINodeMap );
    superBlock.setAddressOfDataBlockMap( addressOfDataBlockMap );
    superBlock.setAddressOfFirstInode( addressOfFirstINode );
    superBlock.setAddressOfFirstDataBlock( addressOfFirstDataBlock );
    superBlock.setTotalBlocks( totalBlocks );

    return superBlock;
  }
}
