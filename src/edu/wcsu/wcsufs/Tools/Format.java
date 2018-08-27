package edu.wcsu.wcsufs.Tools;

import edu.wcsu.wcsufs.Exceptions.CreateDirectoryEntryException;
import edu.wcsu.wcsufs.FSDataStructures.*;
import edu.wcsu.wcsufs.Writers.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class Format
{
  public static void main( String[] args )
  {
    // Sanity check
    if( args.length !=  1 )
    {
      System.out.println( "usage: Format filePath" );
      System.exit( 1 );
    }

    String filePath = args[0];
    File  file = new File( filePath );

    // Sanity check: make sure the file exists
    if( !file.exists() )
    {
      System.out.println( "The file " + file.getAbsolutePath() + " does not exist" );
      System.exit( 2 );
    }

    System.out.println( "The file " + filePath + " is being formatted as WCSU file system" );
    System.out.println( "This will erase all contents of the file" );

    // Get the size of the file
    int fileSize = (int)file.length();

    // Calculate the number of blocks
    int numberOfBlocks = fileSize / FSConstants.BLOCK_SIZE;
    System.out.println( "Number of blocks = "  + numberOfBlocks );

    // We will allocate one-tenth this number of inodes
    int numberOfINodes = numberOfBlocks / 10;

    // We can pack up to 128 INodes in a single block, so round up to the nearest multiple of 128.
    numberOfINodes = ((FSConstants.INODES_PER_BLOCK + numberOfINodes - 1)/ FSConstants.INODES_PER_BLOCK) * FSConstants.INODES_PER_BLOCK;

    // See how many blocks this represents
    int numberOfINodeBlocks = (numberOfINodes * FSConstants.BYTES_PER_INODE) / FSConstants.BLOCK_SIZE;

    // The number of data blocks is then the total number of blocks minus one for the super block, two for the Node Maps,
    // and the number of INode blocks
    int numberOfDataBlocks = numberOfBlocks - (1 + 2 + numberOfINodeBlocks );
    System.out.println( "Number of data blocks is " + numberOfDataBlocks );

    // The address of the INode Map is always 1.
    int addressOfINodeMap = 1;

    // The address of the data node map is always 2.
    int addressOfDataBlockMap = 2;

    // The address of the first INode block is always 3
    int addressOfFirstINode = 3;

    //  The address of the first data block is 3 + the number of INode blocks
    int addressOfFirstDataBlock = 3 + numberOfINodeBlocks;

    // Open a Random Access File
    RandomAccessFile outputFile = null;
    try
    {
      outputFile = new RandomAccessFile( file, "rw" );
    }
    catch( IOException ioe )
    {
      System.out.println( "Caught exception opening random access file" );
      System.exit( 3 );
    }

    // Make up the Super Block
    SuperBlock superblock = new SuperBlock();
    superblock.setMagic( SuperBlock.MAGIC );
    superblock.setNumberOfINodes( numberOfINodes );
    superblock.setNumberOfDataBlocks( numberOfDataBlocks );
    superblock.setAddressOfINodeMap( addressOfINodeMap );
    superblock.setAddressOfDataBlockMap( addressOfDataBlockMap );
    superblock.setAddressOfFirstInode( addressOfFirstINode );
    superblock.setAddressOfFirstDataBlock( addressOfFirstDataBlock );
    superblock.setTotalBlocks( numberOfBlocks );

    try
    {
      WriteSuperBlock.write( outputFile, superblock );
    }
    catch( IOException ioe )
    {
      System.out.println( "Caught exception writing super block" );
    }

    // Create a new INodeMap
    NodeMap INodeMap = new NodeMap( NodeMapType.INodeMap, numberOfINodes );

    // Jump to the start of the INode Map
    try
    {
      outputFile.seek( FSConstants.BLOCK_SIZE );
    }
    catch( IOException ioe )
    {
      System.out.println( "Caught exception jumping to the beginning of the INode Map: " + ioe );
      System.exit( 4 );
    }

    // Write out the INode Map
    try
    {
      WriteNodeMap.write( outputFile, INodeMap );
    }
    catch( IOException ioe )
    {
      System.out.println( "Caught Exception writing INode Map: " + ioe );
      System.exit( 5 );
    }

    // Create a new Data Block Map
    NodeMap dataBlockMap = new NodeMap( NodeMapType.DataBlockMap, numberOfDataBlocks );

    // Jump to the start of the Data Block Map
    try
    {
      outputFile.seek( 2*FSConstants.BLOCK_SIZE );
    }
    catch( IOException ioe )
    {
      System.out.println( "Caught exception jumping to the beginning of the Data Block Map: " + ioe );
      System.exit( 6 );
    }

    // Write out the Data Block Map
    try
    {
      WriteNodeMap.write( outputFile, dataBlockMap );
    }
    catch( IOException ioe )
    {
      System.out.println( "Caught Exception writing Data Blck Map: " + ioe );
      System.exit( 7 );
    }

    // Write out the INodes
    // Jump to the start of the INodes
    try
    {
      outputFile.seek( 3*FSConstants.BLOCK_SIZE );
    }
    catch( IOException ioe )
    {
      System.out.println( "Caught exception jumping to the beginning of the INodes: " + ioe );
      System.exit( 8 );
    }

    // Create a blank INode
    INode blankINode = new INode( 0 );

    // Write it out for each of the needed INodes
    for( int i = 0; i < numberOfINodes; i++ )
    {
      try
      {
        WriteINode.write( outputFile, blankINode );
      }
      catch( IOException ioe )
      {
        System.out.println( "Caught exception writing INode " + i + ": "  + ioe );
        System.exit( 9 );
      }
    }

    // We should be at the start of the data blocks, so no seek is required.

    // Make up a dummy Data Block
    DataBlock blankDataBlock = new DataBlock();
    for( int i = 0; i < numberOfDataBlocks; i++ )
    {
      try
      {
        WriteDataBlock.write( outputFile, blankDataBlock );
      }
      catch( IOException ioe )
      {
        System.out.println( "Caught exception writing data block " + i + ": " + ioe );
        System.exit( 10 );
      }
    }

    // Finally, create the root directory.
    // Allocate INode 0 and the first data block
    INodeMap.setNodeAllocation( true, 0 );
    dataBlockMap.setNodeAllocation( true, 0 );
    try
    {
      outputFile.seek( 1* FSConstants.BLOCK_SIZE );
      WriteNodeMap.write( outputFile, INodeMap );

      outputFile.seek( 2* FSConstants.BLOCK_SIZE );
      WriteNodeMap.write( outputFile, dataBlockMap );
    }
    catch( IOException ioe )
    {
      System.out.println( "Caught exception writing NodeMaps for root: " + ioe );
      System.exit( 11 );
    }

    // Make up the INode
    INode rootINode = new INode( 0 );
    rootINode.setAccessMode( AccessConstants.read | AccessConstants.write | AccessConstants.execute );
    rootINode.setNumberOfLinks( 0 );
    rootINode.setFileSize( FSConstants.BLOCK_SIZE );
    rootINode.setType( INodeType.Directory );
    rootINode.addDirectPointer( addressOfFirstDataBlock );
    rootINode.setAllocatedBlocks( 1 );

    try
    {
      outputFile.seek( 3 * FSConstants.BLOCK_SIZE );
      WriteINode.write( outputFile, rootINode );
    }
    catch( IOException ioe )
    {
      System.out.println( "Caught exception writing root INode: " + ioe );
      System.exit( 12 );
    }

    // Make up its corresponding data block
    DirectoryDataBlock rootDataBlock = new DirectoryDataBlock();
    try
    {
      if( rootDataBlock.createDirectoryEntry( 0, "." ) )
      {
        System.out.println( "Writing root data block with entries " + rootDataBlock.getDirectoryEntries() );
        outputFile.seek( 4* FSConstants.BLOCK_SIZE );
        WriteDirectoryBlock.write( outputFile, rootDataBlock );
      }
    }
    catch( CreateDirectoryEntryException cdee )
    {
      System.out.println( "Caught exception writing Directory entry for root" );
    }
    catch( IOException ioe )
    {
      System.out.println( "Caught IO exception writing Directory Data Block for root" );
    }


    // That's it!
    try
    {
      outputFile.close();
    }
    catch( IOException ioe )
    {
      System.out.println( "Caught exception closing file" );
    }



  }
}
