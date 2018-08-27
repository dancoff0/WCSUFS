package edu.wcsu.wcsufs.Tools;

import edu.wcsu.wcsufs.Exceptions.*;
import edu.wcsu.wcsufs.FSDataStructures.*;
import edu.wcsu.wcsufs.FSDataStructures.FileDescriptor;
import edu.wcsu.wcsufs.Readers.ReadINode;
import edu.wcsu.wcsufs.Readers.ReadNodeMap;
import edu.wcsu.wcsufs.Readers.ReadSuperBlock;
import edu.wcsu.wcsufs.Writers.WriteDataBlock;
import edu.wcsu.wcsufs.Writers.WriteINode;
import edu.wcsu.wcsufs.Writers.WriteNodeMap;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Shell
{

  // Member variables
  private RandomAccessFile inputFile = null;
  private SuperBlock  superBlock;
  private NodeMap     INodeMap;
  private NodeMap     dataBlockMap;
  private INode[]     INodes;
  private DataBlock[] dataBlocks;

  private int numberOfINodes;
  private int numberOfDataBlocks;
  private int addressOfFirstDataBlock;
  private int addressOfFirstINode;
  private int addressOfINodeMap;
  private int addressOfDataBlockMap;

  // This is the INode of the current directory
  private INode currentDirectory = null;

  // Parsers for individual commands
  private CommandLineParser cliParser    = null;
  private Options           lsOptions    = null;
  private Options           mkdirOptions = null;
  private Options           rmOptions    = null;

  // These variables are used to control the interactive shell
  private volatile boolean moribund = false;

  public void mount( File wcsuFile ) throws IncorrectMagicException, IOException
  {
    // Create the input file
    inputFile = new RandomAccessFile( wcsuFile, "rw" );

    // Read the super block
    SuperBlock superBlock = ReadSuperBlock.read( inputFile );

    /*
    System.out.println( "Read Super Block" );
    System.out.println( "\tNumber of INodes = "            + superBlock.getNumberOfINodes()          );
    System.out.println( "\tNumber of Data Blocks = "       + superBlock.getNumberOfDataBlocks()      );
    System.out.println( "\tAddress of INode Map = "        + superBlock.getAddressOfINodeMap()       );
    System.out.println( "\tAddress of Data Block Map = "   + superBlock.getAddressOfDataBlockMap()   );
    System.out.println( "\tAddress of first INode = "      + superBlock.getAddressOfFirstInode()     );
    System.out.println( "\tAddress of first Data Block = " + superBlock.getAddressOfFirstDataBlock() );
    System.out.println( "\tTotal blocks = "                + superBlock.getTotalBlocks()             );
    */
    addressOfFirstDataBlock = superBlock.getAddressOfFirstDataBlock();
    addressOfFirstINode     = superBlock.getAddressOfFirstInode();
    addressOfINodeMap       = superBlock.getAddressOfINodeMap();
    addressOfDataBlockMap   = superBlock.getAddressOfDataBlockMap();

    // Read the Node Maps
    // INode map first
    inputFile.seek( addressOfINodeMap * FSConstants.BLOCK_SIZE );
    numberOfINodes = superBlock.getNumberOfINodes();
    INodeMap = ReadNodeMap.read( inputFile, NodeMapType.INodeMap, numberOfINodes );
    //System.out.println( "Read INode Map"  );
   //System.out.println( "\tfirst unallocated INode is " + INodeMap.getNodeMap().nextClearBit( 0 ) );

    // Now DataBlock Map
    inputFile.seek( addressOfDataBlockMap * FSConstants.BLOCK_SIZE );
    numberOfDataBlocks = superBlock.getNumberOfDataBlocks();
    dataBlockMap = ReadNodeMap.read( inputFile, NodeMapType.DataBlockMap, numberOfDataBlocks );
    //System.out.println( "Read Data Block Map"  );
    //System.out.println( "\tfirst unallocated DataBlock is " + dataBlockMap.getNodeMap().nextClearBit( 0 ) );

    // Read the INodes
    INodes = new INode[ numberOfINodes ];
    for( int i = 0; i < numberOfINodes; i++ )
    {
      if( INodeMap.getNodeMap().get( i ) )
      {
        int INodeAddress = addressOfFirstINode * FSConstants.BLOCK_SIZE + i*FSConstants.BYTES_PER_INODE;
        inputFile.seek( INodeAddress );
        INodes[i] = ReadINode.read( inputFile, i );
      }
      else
      {
        INodes[i] = new INode( i );
      }
    }

    // Read the allocated Data Blocks
    dataBlocks = new DataBlock[ numberOfDataBlocks ];
    for( int i = 0; i < numberOfDataBlocks; i++ )
    {
      dataBlocks[i] = null;
    }

    // Make a temporary array for reading in the data.
    byte[] temp = new byte[ FSConstants.BLOCK_SIZE ];

    for( INode inode : INodes )
    {
      if( inode.getType() == INodeType.Unused )
      {
        continue;
      }

      if( inode.getType() == INodeType.Directory )
      {
        // Get the directory data blocks
        int allocatedBlocks = inode.getAllocatedBlocks();
        //System.out.println( "For this directory " + inode.getINodeNumber() + ", need to read " + allocatedBlocks + " block" );

        // Sanity check
        if( allocatedBlocks > 0 )
        {
          // First get the direct data blocks
          for( int dataBlockPointer : inode.getDirectPointers() )
          {
            //System.out.println( "dataBlockPointer = " + dataBlockPointer );
            int dataBlockIndex = dataBlockPointer - addressOfFirstDataBlock;
            if( dataBlockIndex < 0 || dataBlockIndex >= numberOfDataBlocks ) continue;
            //System.out.println( "dataBlockIndex = " + dataBlockIndex );

            // Make sure this has not already been read in
            if( dataBlocks[ dataBlockIndex ] != null )
            {
              continue;
            }

            //System.out.println( "Reading in new DirectoryDataBlock for index " + dataBlockIndex );
            DirectoryDataBlock dataBlock = new DirectoryDataBlock();
            inputFile.seek(  dataBlockPointer  * FSConstants.BLOCK_SIZE );
            inputFile.read( temp, 0, FSConstants.BLOCK_SIZE );
            dataBlock.setData( temp );
            dataBlocks[ dataBlockIndex ] = dataBlock;
          }
        }

        int indirectPointer = inode.getIndirectPointer();
        if( indirectPointer >= addressOfFirstDataBlock )
        {
          // Check that the data block for this has already be read in
          DataBlock pointerBlock = null;
          int indirectIndex = indirectPointer - addressOfFirstDataBlock;
          if( dataBlocks[ indirectIndex ] == null )
          {
            // Need to read in this data block
            inputFile.seek( indirectPointer * FSConstants.BLOCK_SIZE );
            inputFile.read( temp, 0, FSConstants.BLOCK_SIZE );
            pointerBlock = new DataBlock();
            pointerBlock.setData( temp );
            dataBlocks[ indirectIndex ] = pointerBlock;
          }
          else
          {
            pointerBlock = dataBlocks[ indirectIndex ];
          }

          temp = pointerBlock.getData();
          for( int i = 0; i < FSConstants.BLOCK_SIZE; i += 4 )
          {
            int dataBlockPointer = ByteBuffer.wrap( temp, i, 4 ).getInt();
            if( dataBlockPointer < addressOfFirstDataBlock )
            {
              break;
            }

            int dataBlockIndex = dataBlockPointer - addressOfFirstDataBlock;

            // See if this data block has already been read
            if( dataBlocks[ dataBlockIndex ] != null )
            {
              continue;
            }

            byte[] newData = new byte[ FSConstants.BLOCK_SIZE ];
            inputFile.seek( dataBlockPointer  * FSConstants.BLOCK_SIZE );
            inputFile.read( newData, 0, FSConstants.BLOCK_SIZE );
            DirectoryDataBlock newDataBlock = new DirectoryDataBlock();
            newDataBlock.setData( newData );
            dataBlocks[ dataBlockIndex ] = newDataBlock;
          }
        }
      }
      else if( inode.getType() == INodeType.File )
      {
        // Get the data blocks
        int allocatedBlocks = inode.getAllocatedBlocks();

        // Sanity check
        if( allocatedBlocks >= 0 )
        {
          // First get the direct data blocks
          for( int dataBlockPointer : inode.getDirectPointers() )
          {
            if( dataBlockPointer < addressOfFirstDataBlock ) continue;
            int dataBlockIndex = dataBlockPointer - addressOfFirstDataBlock;

            // Make sure this has not already been read in
            if( dataBlocks[ dataBlockIndex ] != null )
            {
              continue;
            }

            DataBlock dataBlock = new DataBlock();
            inputFile.seek(  dataBlockPointer * FSConstants.BLOCK_SIZE );
            inputFile.read( temp, 0, FSConstants.BLOCK_SIZE );
            dataBlock.setData( temp );
            dataBlocks[ dataBlockIndex ] = dataBlock;
          }
        }

        int indirectPointer = inode.getIndirectPointer();
        if( indirectPointer >= addressOfFirstDataBlock )
        {
          // Check that the data block for this has already be read in
          DataBlock pointerBlock = null;
          int indirectIndex = indirectPointer - addressOfFirstDataBlock;
          if( dataBlocks[ indirectIndex ] == null )
          {
            // Need to read in this data block
            inputFile.seek(  indirectPointer * FSConstants.BLOCK_SIZE );
            inputFile.read( temp, 0, FSConstants.BLOCK_SIZE );
            pointerBlock = new DataBlock();
            pointerBlock.setData( temp );
            dataBlocks[ indirectIndex] = pointerBlock;
          }
          else
          {
            pointerBlock = dataBlocks[ indirectIndex ];
          }

          temp = pointerBlock.getData();
          for( int i = 0; i < FSConstants.BLOCK_SIZE; i += 4 )
          {
            int dataBlockPointer = ByteBuffer.wrap( temp, i, 4 ).getInt();
            if( dataBlockPointer < addressOfFirstDataBlock )
            {
              break;
            }

            // See if this data block has already been read
            int dataBlockIndex = dataBlockPointer - addressOfFirstDataBlock;
            if( dataBlocks[ dataBlockIndex ] != null )
            {
              continue;
            }

            byte[] newData = new byte[ FSConstants.BLOCK_SIZE ];
            inputFile.seek( dataBlockPointer * FSConstants.BLOCK_SIZE );
            inputFile.read( newData, 0, FSConstants.BLOCK_SIZE );
            DataBlock newDataBlock = new DataBlock();
            newDataBlock.setData( newData );
            dataBlocks[ dataBlockIndex] = newDataBlock;
          }
        }
      }
    }

    // That's it! We are mounted!
    // Now just set the current directory to be '/'
    currentDirectory = INodes[0];
  }

  public void interactive() throws Exception
  {
    moribund = false;
    Scanner inputScanner = new Scanner( System.in );

    while( !moribund )
    {
      // Issue a prompt
      System.out.print( "> " );

      // Read in the response.
      String response = inputScanner.nextLine();

      // Check if we should exit
      if( "exit".equalsIgnoreCase( response ) )
      {
        moribund = true;
        continue;
      }

      String[] responseComponents = response.split( " ");

      String command = responseComponents[0];

      try
      {
        switch( command )
        {
          case "ls":
            handleLS( responseComponents );
            break;

          case "mkdir":
            handleMKDIR( responseComponents );
            break;

          case "cd":
            handleCD( responseComponents );
            break;

          case "pwd":
            handlePWD( responseComponents );
            break;

          case "import":
            handleIMPORT( responseComponents );
            break;

          case "cat":
            handleCAT( responseComponents );
            break;

          case "export":
            handleExport( responseComponents );
            break;

          case "rm":
            handleRM( responseComponents );
            break;
        }
      }
      catch( Exception e )
      {
        //System.out.println( "Caught exception executing " + command + ": "+ e );
        System.out.println( e );
        //e.printStackTrace();
      }
    }
  }

  // Individual command handlers
  private void handleRM( String[] args ) throws Exception
  {
    // Check if the parser is ready
    if( cliParser == null )
    {
      cliParser = new DefaultParser();
    }

    if( rmOptions == null )
    {
      rmOptions = new Options();
      rmOptions.addOption( "r", "recursive", false, "remove all intermediate directories and files recursively" );
    }

    // Parse the command line options.
    // The only possible argument is -r
    boolean removeRecursive = false;
    //System.out.println( "Parsing ls args: " );
    //for( String arg : args )
    // {
    //  System.out.println( "\t" + arg );
    //}
    CommandLine commandLine = cliParser.parse( rmOptions, args );
    if( commandLine.hasOption( 'r' ) ) removeRecursive = true;

    //System.out.println( "For rm command, options are" );
    //System.out.println( "\tremoveRecursive = " + removeRecursive );

    // See if a path was specified
    List<String> remainingArguments = commandLine.getArgList();
    //System.out.println( "Remaining arguments" );
    //for( String remainingArgument : remainingArguments )
    //{
    //  System.out.println( "\t" + remainingArgument );
    //}
    if( remainingArguments.size() != 2 )
    {
      System.out.println( "usage: rm [-r] FILE-SPECIFICATION" );
      return;
    }
    String fileSpec = remainingArguments.get(1);

    // Find the corresponding INode
    INode desiredINode = null;
    try
    {
      desiredINode = findINode( fileSpec, currentDirectory );
    }
    catch( Exception e )
    {
      System.out.println(  e.getMessage() );
      return;
    }

    if( desiredINode == null )
    {
      System.out.println( "Could not find " + fileSpec );
      return;
    }

    // Get the containing directory. We need this the remove the hard link to our file.
    INode parentINode = null;
    String fileName;

    int lastSlashIndex = fileSpec.lastIndexOf( '/' );
    if( lastSlashIndex < 0 )
    {
      parentINode = currentDirectory;
      fileName    = fileSpec;
    }
    else
    {
      String parentDirectory = fileSpec.substring( 0, lastSlashIndex );
      parentINode = findINode( parentDirectory, currentDirectory );
      fileName    = fileSpec.substring( lastSlashIndex + 1 );
    }

    // Proceed according to the file type
    if( desiredINode.getType() == INodeType.File )
    {
      removeHardlink( parentINode, fileName );

      int hardlinkCount = desiredINode.getNumberOfLinks();
      if( hardlinkCount == 1 )
      {
        // If there was only one hard link, then delete the INode
        removeFile( desiredINode );
      }
      else
      {
        // Otherwise, just decrease the hard link count.
        desiredINode.setNumberOfLinks( hardlinkCount - 1 );
      }
    }
    else if( desiredINode.getType() == INodeType.Directory )
    {
      // TODO: need to delete directory.
      // We can only delete a directory if it is empty or if the -r option is not specified.
      if( !isEmpty( desiredINode ) )
      {
        if( !removeRecursive )
        {
          System.out.println( "Directory " + fileName + " is not empty" );
        }
        else
        {
          removeDirectory( desiredINode, parentINode, fileName );
          removeHardlink( parentINode, fileName );
        }
      }
      else
      {
        // Directory is empty
        removeHardlink( parentINode, fileName );
        removeFile( desiredINode );
      }

    }
  }

  private void handleCAT( String[] args ) throws Exception
  {
    // cat has no options; it requires a file specification
    if( args.length != 2 )
    {
      System.out.println( "useage: cat FILESPEC" );
      return;
    }

    // Get the file specification
    String fileSpec = args[1];

    // Find the corresponding INode
    INode desiredINode = findINode( fileSpec, currentDirectory );
    if( desiredINode == null )
    {
      System.out.println( "Could not find file: " + fileSpec );
      return;
    }

    // Sanity check: this must be a normal file
    if( desiredINode.getType() != INodeType.File )
    {
      System.out.println( "cat may only be used with an ordinary file" );
      return;
    }

    // Loop over the data blocks and print them out
    int   fileSize       = desiredINode.getFileSize();
    int[] directPointers = desiredINode.getDirectPointers();
    int   bytesToBeWritten = fileSize;
    for( int directPointer : directPointers )
    {
      if( directPointer < addressOfFirstDataBlock ) continue;
      byte[] data = dataBlocks[ directPointer - addressOfFirstDataBlock ].getData();
      String currentPart = "";
      if( bytesToBeWritten >= data.length )
      {
        currentPart = new String( data );
        bytesToBeWritten -= data.length;
      }
      else
      {
        currentPart = new String( data, 0, bytesToBeWritten );
        bytesToBeWritten = 0;
      }

      System.out.print( currentPart );

      // If we have already written everything, there is no point of continuing the loop.
      if( bytesToBeWritten == 0 )
      {
        break;
      }
    }

    //System.out.println( "cat: finished with direct blocks: bytesToBeWritten = " + bytesToBeWritten );

    // Check if we need to go into the indirect pointers
    if( bytesToBeWritten > 0 )
    {
      // Get the indirect pointer
      int indirectPointer = desiredINode.getIndirectPointer();

      // Sanity check
      if( indirectPointer < addressOfFirstDataBlock )
      {
        throw new InvalidIndirectPointerException( "Indirect Pointer " + indirectPointer + " is invalid" );
      }

      // Get the indirect pointer block
      DataBlock indirectPointerBlock = dataBlocks[ indirectPointer - addressOfFirstDataBlock ];
      if( indirectPointerBlock == null )
      {
        throw new InvalidIndirectPointerException( "Indirect Pointer Block is empty" );
      }

      byte[] indirectPointers = indirectPointerBlock.getData();

      // Loop over the indirect blocks
      int blockPointerOffset = 0;
      while( bytesToBeWritten > 0 )
      {
        int blockPointer = ByteBuffer.wrap( indirectPointers, blockPointerOffset, 4).getInt();
        //System.out.println( "Displaying data block " + blockPointer );
        if( blockPointer < addressOfFirstDataBlock )
        {
          throw new IndirectBlockMissingException( "The indirect block pointer is invalid" );
        }

        // Get the data block
        DataBlock dataBlock = dataBlocks[ blockPointer - addressOfFirstDataBlock ];
        if( dataBlock == null )
        {
          throw new IndirectBlockMissingException( "An indirect data block is missing" );
        }

        // Now get and print out the data
        byte[] data = dataBlock.getData();
        String currentPart = "";

        if( bytesToBeWritten >= data.length )
        {
          currentPart = new String( data );
          //System.out.println( "Writing indirect block of length " + currentPart.length() );
          bytesToBeWritten -= data.length;
        }
        else
        {
          currentPart = new String( data, 0, bytesToBeWritten );
          //System.out.println( "Writing indirect block of length " + currentPart.length() );
          //System.out.println( currentPart );
          bytesToBeWritten = 0;
        }

        System.out.print( currentPart );

        // If we have already written everything, there is no point of continuing the loop.
        if( bytesToBeWritten == 0 )
        {
          break;
        }

        blockPointerOffset += 4;
      }


    }
  }

  private void handleExport( String[] args ) throws Exception
  {
    // export has no options; it requires an input and an output file specification
    if( args.length != 3 )
    {
      System.out.println( "useage: export LOCAL-FILESPEC REMOTE-FILESPEC" );
      return;
    }

    // Get the remote file specification --- this is OS dependent
    String remoteFileSpec = args[2];

    // Make sure the file does not already exist
    File remoteFile = new File( remoteFileSpec );
    if( remoteFile.exists() )
    {
      System.out.println( "File " + remoteFileSpec + " already exists" );
      return;
    }

    // Make sure the directory that contains the remote file exists and is writable
    remoteFile.createNewFile();

    FileWriter fileWriter = new FileWriter( remoteFile );

    // Get the local file specification
    String localFileSpec = args[1];

    // Find the corresponding INode
    INode desiredINode = findINode( localFileSpec, currentDirectory );
    if( desiredINode == null )
    {
      System.out.println( "Could not find file: " + localFileSpec );
      return;
    }


    // Sanity check: this must be a normal file
    if( desiredINode.getType() != INodeType.File )
    {
      System.out.println( "export may only be used with an ordinary file" );
      return;
    }

    // Loop over the data blocks and print them out
    int   fileSize       = desiredINode.getFileSize();
    int[] directPointers = desiredINode.getDirectPointers();
    int   bytesToBeWritten = fileSize;
    for( int directPointer : directPointers )
    {
      if( directPointer < addressOfFirstDataBlock ) continue;
      byte[] data = dataBlocks[ directPointer - addressOfFirstDataBlock ].getData();
      String currentPart = "";
      if( bytesToBeWritten >= data.length )
      {
        currentPart = new String( data );
        bytesToBeWritten -= data.length;
      }
      else
      {
        currentPart = new String( data, 0, bytesToBeWritten );
        bytesToBeWritten = 0;
      }

      fileWriter.write( currentPart );

      // If we have already written everything, there is no point of continuing the loop.
      if( bytesToBeWritten == 0 )
      {
        break;
      }
    }

    //System.out.println( "export: finished with direct blocks: bytesToBeWritten = " + bytesToBeWritten );

    // Check if we need to go into the indirect pointers
    if( bytesToBeWritten > 0 )
    {
      // Get the indirect pointer
      int indirectPointer = desiredINode.getIndirectPointer();

      // Sanity check
      if( indirectPointer < addressOfFirstDataBlock )
      {
        throw new InvalidIndirectPointerException( "Indirect Pointer " + indirectPointer + " is invalid" );
      }

      // Get the indirect pointer block
      DataBlock indirectPointerBlock = dataBlocks[ indirectPointer - addressOfFirstDataBlock ];
      if( indirectPointerBlock == null )
      {
        throw new InvalidIndirectPointerException( "Indirect Pointer Block is empty" );
      }

      byte[] indirectPointers = indirectPointerBlock.getData();

      // Loop over the indirect blocks
      int blockPointerOffset = 0;
      while( bytesToBeWritten > 0 )
      {
        int blockPointer = ByteBuffer.wrap( indirectPointers, blockPointerOffset, 4).getInt();
        //System.out.println( "Displaying data block " + blockPointer );
        if( blockPointer < addressOfFirstDataBlock )
        {
          throw new IndirectBlockMissingException( "The indirect block pointer is invalid" );
        }

        // Get the data block
        DataBlock dataBlock = dataBlocks[ blockPointer - addressOfFirstDataBlock ];
        if( dataBlock == null )
        {
          throw new IndirectBlockMissingException( "An indirect data block is missing" );
        }

        // Now get and print out the data
        byte[] data = dataBlock.getData();
        String currentPart = "";

        if( bytesToBeWritten >= data.length )
        {
          currentPart = new String( data );
          //System.out.println( "Writing indirect block of length " + currentPart.length() );
          bytesToBeWritten -= data.length;
        }
        else
        {
          currentPart = new String( data, 0, bytesToBeWritten );
          //System.out.println( "Writing indirect block of length " + currentPart.length() );
          //System.out.println( currentPart );
          bytesToBeWritten = 0;
        }

        fileWriter.write( currentPart );

        // If we have already written everything, there is no point of continuing the loop.
        if( bytesToBeWritten == 0 )
        {
          break;
        }

        blockPointerOffset += 4;
      }

    }
    fileWriter.close();
  }


  private void handleIMPORT( String[] args ) throws Exception
  {
    // import has no options; it requires an input and an output file specification
    if( args.length != 3 )
    {
      System.out.println( "useage: import REMOTE-FILESPEC LOCAL-FILESPEC" );
      return;
    }

    // Get the remote file specification --- this is OS dependent
    String remoteFileSpec = args[1];

    // Make sure the file is available
    File remoteFile = new File( remoteFileSpec );
    if( !remoteFile.exists() )
    {
      System.out.println( "Could not find " + remoteFileSpec );
      return;
    }

    // Get the local file specification
    String localFileSpec = args[2];

    // Remove the last part --- the file name and then find the INode for the directory that is to contain it.
    int lastSlashIndex = localFileSpec.lastIndexOf( '/');
    String localDirectory;
    String localFilename;
    if( lastSlashIndex >= 0 )
    {
      localDirectory = localFileSpec.substring( 0, lastSlashIndex );
      localFilename  = localFileSpec.substring( lastSlashIndex + 1 );
    }
    else
    {
      localDirectory = ".";
      localFilename  = localFileSpec;
    }

    // Find the INode corresponding to the local directory
    INode localDirectoryINode = findINode( localDirectory, currentDirectory );

    // Make sure the file does not already exist
    List<FileDescriptor> fileDescriptors = getDirectoryContents( currentDirectory );
    for( FileDescriptor fileDescriptor : fileDescriptors )
    {
      if( fileDescriptor.getName().trim().equals( localFilename ) )
      {
        System.out.println( "File " + localFilename + " already exists" );
        return;
      }
    }

    // Allocate an INode for this file
    int nextUnsedINodeIndex = INodeMap.getNodeMap().nextClearBit( 0 );
    if( nextUnsedINodeIndex < 0 )
    {
      throw new OutOfINodesException( "Could not allocate an INode for this file" );
    }

    // Mark this INode as in use
    INodeMap.getNodeMap().set( nextUnsedINodeIndex );

    // Now create the INode
    INode localFileINode = new INode( nextUnsedINodeIndex );
    INodes[ nextUnsedINodeIndex ] = localFileINode;
    localFileINode.setAccessMode( AccessConstants.read | AccessConstants.write | AccessConstants.execute );
    localFileINode.setNumberOfLinks( 1 );
    localFileINode.setFileSize( 0 );
    localFileINode.setType( INodeType.File );

    // We need to create a directory entry for this in the directory
    boolean entryCreated                                 = false;
    DirectoryDataBlock  updatedDirectoryDataBlock        = null;
    int                 updatedDirectoryDataBlockPointer = -1;
    boolean             localDirectoryINodeUpdated       = false;
    int[] directPointers = localDirectoryINode.getDirectPointers();
    for( int i = 0; i < directPointers.length; i++ )
    {
      int directPointer = localDirectoryINode.getDirectPointer( i );
      if( directPointer >= addressOfFirstDataBlock )
      {
        DirectoryDataBlock dataBlock = (DirectoryDataBlock)dataBlocks[directPointer - addressOfFirstDataBlock];
        if( dataBlock == null ) continue;

        // We got a valid directory block.  Try to write our pointer to it.
        if( dataBlock.createDirectoryEntry( localFileINode.getINodeNumber(), localFilename ) )
        {
          entryCreated                     = true;
          updatedDirectoryDataBlockPointer = directPointer;
          updatedDirectoryDataBlock        = dataBlock;
          break;
        }
      }
      else
      {
        // Add a new DirectoryDataBlock
        DirectoryDataBlock dataBlock = new DirectoryDataBlock();

        // We need to find an unused data block
        int nextFreeDataBlockIndex = dataBlockMap.getNodeMap().nextClearBit(0 );

        // Sanity check
        if( nextFreeDataBlockIndex < 0 )
        {
          throw new OutOfDataBlocksException( "The filesystem is out of free data blocks" );
        }

        // Mark this data block as in use
        dataBlockMap.getNodeMap().set( nextFreeDataBlockIndex );

        // Add the new data block to the list of data blocks
        directPointer = nextFreeDataBlockIndex + addressOfFirstDataBlock;
        dataBlocks[ nextFreeDataBlockIndex ] = dataBlock;
        localDirectoryINode.setDirectPointer( i, directPointer );

        //  This next block of code should always work, but we'll leave the 'if' anyway.
        if( dataBlock.createDirectoryEntry( localFileINode.getINodeNumber(), localFilename ) )
        {
          entryCreated                     = true;
          updatedDirectoryDataBlockPointer = directPointer;
          updatedDirectoryDataBlock        = dataBlock;
          localDirectoryINodeUpdated       = true;
          break;
        }
      }
    }

    // Check if we successfully created the directory entry
    if( !entryCreated )
    {
      // TODO: allocate a new block from the indirect block if the direct blocks are all full.
    }

    // Now we need to read the file, and write its contents to data blocks in our file system
    byte[] buffer = new byte[ FSConstants.BLOCK_SIZE ];
    FileInputStream remoteFileStream = new FileInputStream( remoteFile );
    int fileSize        = 0;
    int blocksAllocated = 0;
    boolean indirectPointerBlockAdded = false;
    while( true )
    {
      // Read in a block of data
      int bytesRead = remoteFileStream.read( buffer, 0, FSConstants.BLOCK_SIZE );
      if( bytesRead <= 0 ) break;

      // Allocate a data block
      int nextUnusedDataBlockIndex = dataBlockMap.getFirstUnallocatedBlock();
      if( nextUnusedDataBlockIndex < 0 )
      {
        throw new OutOfDataBlocksException( "There are no more unused data blocks" );
      }
      //System.out.println( "Allocating data block " + nextUnusedDataBlockIndex );

      // Mark this block as now in use
      dataBlockMap.getNodeMap().set( nextUnusedDataBlockIndex );

      // Make up a data block
      DataBlock newDataBlock = new DataBlock();
      newDataBlock.setData( buffer );
      int newDataBlockIndex = nextUnusedDataBlockIndex;
      dataBlocks[ newDataBlockIndex ] = newDataBlock;
      //System.out.println( "Added new data block to array of data blocks at index " + newDataBlockIndex );

      // Add it to the INode
      IndirectPointerBlock indirectPointerBlock = null;
      if( !localFileINode.addDirectPointer( addressOfFirstDataBlock + newDataBlockIndex ) )
      {
        // This happens if the direct blocks are already full.
        // Try to add it to the indirect block
        int indirectPointer = localFileINode.getIndirectPointer();
        if( indirectPointer < addressOfFirstDataBlock )
        {
          // The indirect pointer is not yet set.
          int indirectIndex = dataBlockMap.getFirstUnallocatedBlock();
          if( indirectIndex < 0 )
          {
            throw new OutOfDataBlocksException( "Could not allocate an indirect pointer block: there are no more free data blocks!" );
          }
          dataBlockMap.getNodeMap().set( indirectIndex );
          indirectPointer = indirectIndex + addressOfFirstDataBlock;
          localFileINode.setIndirectPointer( indirectPointer );

          // Now allocate an indirect pointer block
          indirectPointerBlock = new IndirectPointerBlock( addressOfFirstDataBlock );
          dataBlocks[ indirectIndex ] = indirectPointerBlock;
          indirectPointerBlockAdded = true;
        }
        else
        {
          indirectPointerBlock = (IndirectPointerBlock)dataBlocks[ indirectPointer - addressOfFirstDataBlock ];
        }

        indirectPointerBlock.addIndirectPointer(  newDataBlockIndex + addressOfFirstDataBlock );
      }

      blocksAllocated++;
      fileSize += bytesRead;

      if( bytesRead < FSConstants.BLOCK_SIZE )
      {
        break;
      }
    }

    localFileINode.setFileSize( fileSize );
    localFileINode.setAllocatedBlocks( blocksAllocated );

    remoteFileStream.close();
    //System.out.println( "Synchronizing" );

    // Now, we just need to synchronize the data structures with the actual file system.
    // INodeMap
    inputFile.seek( addressOfINodeMap * FSConstants.BLOCK_SIZE );
    WriteNodeMap.write( inputFile, INodeMap );

    // DataBlockMap
    inputFile.seek(addressOfDataBlockMap * FSConstants.BLOCK_SIZE );
    WriteNodeMap.write( inputFile, dataBlockMap );

    // INode for the directory --- if updated
    if( localDirectoryINodeUpdated )
    {
      inputFile.seek( addressOfFirstINode*FSConstants.BLOCK_SIZE + localDirectoryINode.getINodeNumber()*FSConstants.BYTES_PER_INODE );
      WriteINode.write( inputFile, localDirectoryINode );
    }

    // New INode for file
    inputFile.seek( addressOfFirstINode*FSConstants.BLOCK_SIZE + localFileINode.getINodeNumber()*FSConstants.BYTES_PER_INODE );
    WriteINode.write( inputFile, localFileINode );
    System.out.println( "Finished writing INode " + localFileINode.getINodeNumber() + " for new file" );

    // DirectoryDataBlock for directory (updated or newly created)
    inputFile.seek( updatedDirectoryDataBlockPointer * FSConstants.BLOCK_SIZE );
    try
    {


      WriteDataBlock.write( inputFile, updatedDirectoryDataBlock );
    }
    catch( Exception e )
    {
      System.out.println( "Caught exception: "+ e );
      e.printStackTrace();
    }
    //System.out.println( "Finished write directory data block" );

    // Data Blocks for file itself
    //      Direct blocks
    for( int directPointer : localFileINode.getDirectPointers() )
    {
      if( directPointer >= addressOfFirstDataBlock )
      {
        inputFile.seek( directPointer * FSConstants.BLOCK_SIZE );
        WriteDataBlock.write( inputFile, dataBlocks[ directPointer - addressOfFirstDataBlock ] );
      }
    }

    //System.out.println( "Finished writing direct blocks" );

    //     Indirect blocks
    int indirectPointer = localFileINode.getIndirectPointer();
    if( indirectPointer < addressOfFirstDataBlock )
    {
      // There are no indirect blocks, so we are done.
      return;
    }

    IndirectPointerBlock indirectPointerBlock = (IndirectPointerBlock)dataBlocks[ indirectPointer - addressOfFirstDataBlock ];
    if( indirectPointerBlockAdded )
    {
      inputFile.seek( indirectPointer*FSConstants.BLOCK_SIZE );
      WriteDataBlock.write( inputFile, indirectPointerBlock );
    }
    List<Integer> indirectPointers = indirectPointerBlock.getIndirectPointers();
    for( int currentIndirectPointer : indirectPointers )
    {
      if( currentIndirectPointer < addressOfFirstDataBlock )
      {
        continue;
      }
      DataBlock currentDataBlock = dataBlocks[ currentIndirectPointer - addressOfFirstDataBlock ];
      inputFile.seek( currentIndirectPointer * FSConstants.BLOCK_SIZE );
      WriteDataBlock.write( inputFile, currentDataBlock );
    }
  }
  private void handlePWD( String[] args ) throws Exception
  {
    // pwd doesn't have any options
    if( args.length != 1 )
    {
      System.out.println( "usage: pwd" );
      return;
    }

    // Start in current directory and move backwards until we hit a directory that doesn't have a parent, the root!
    String fullPath = null;
    INode tempDirectory = currentDirectory;
    int inodeNumber     = currentDirectory.getINodeNumber();
    List<FileDescriptor> fileDescriptors = getDirectoryContents( currentDirectory );
    while( true )
    {

      INode parentDirectory = null;
      for( FileDescriptor fileDescriptor : fileDescriptors )
      {
        if( fileDescriptor.getName().trim().equals( ".." ) )
        {
          parentDirectory = INodes[fileDescriptor.getINodeNumber()];
        }
      }

      if( parentDirectory == null ) break;

      fileDescriptors = getDirectoryContents( parentDirectory );

      for( FileDescriptor fileDescriptor : fileDescriptors )
      {
        if( fileDescriptor.getINodeNumber() == inodeNumber )
        {
          if( fullPath == null )
          {
            fullPath = "/" + fileDescriptor.getName();
          }
          else
          {
            fullPath = "/" + fileDescriptor.getName() + fullPath;
          }
        }
      }

      inodeNumber = parentDirectory.getINodeNumber();
    }

    // Check if we're actually at the root.
    if( fullPath == null )
    {
      fullPath = "/";
    }

    // That's it
    System.out.println( fullPath );

  }


  private void handleCD( String[] args ) throws Exception
  {
    // cd doesn't have any options so just get the file path
    if( args.length != 2 )
    {
      System.out.println( "usage: cd FILEPATH" );
      return;
    }

    // Get the filepath
    String filePath = args[1];

    INode newINode = findINode( filePath, currentDirectory );

    /*
    // This must be represented by one of the entries in the current directory
    List<FileDescriptor> fileDescriptors = getDirectoryContents( currentDirectory );

    INode newWorkingDirectory = null;
    for( FileDescriptor fileDescriptor : fileDescriptors )
    {
      System.out.println( "Comparing |" + filePath + "| and |" + fileDescriptor.getName() + "|");
      if( fileDescriptor.getName().trim().equals( filePath ) )
      {
        System.out.println( "Found matching name for INode " + fileDescriptor.getINodeNumber() );
        newWorkingDirectory = INodes[ fileDescriptor.getINodeNumber() ];
        break;
      }
    }
    */

    if( newINode != null )
    {
      // Check that this is a directory node
      if( newINode.getType() == INodeType.Directory )
      {
        currentDirectory = newINode;
      }
      else
      {
        System.out.println( filePath + " is not a directory" );
      }
    }
    else
    {
      System.out.println( "Could not find " + filePath );
    }
  }
  private void handleMKDIR( String[] args ) throws Exception
  {
    // Check if the parser is ready
    if( cliParser == null )
    {
      cliParser = new DefaultParser();
    }

    if( mkdirOptions == null )
    {
      mkdirOptions = new Options();
      mkdirOptions.addOption( "p", "permissive",  false, "create all intermediate directories" );
    }

    // Parse the command line options.
    // The only possible argument is -p
    boolean createPermissive = false;
    //System.out.println( "Parsing ls args: " );
    //for( String arg : args )
    // {
    //  System.out.println( "\t" + arg );
    //}
    CommandLine commandLine = cliParser.parse( mkdirOptions, args );
    if( commandLine.hasOption( 'p' ) ) createPermissive = true;

    //System.out.println( "For mkdir command, options are" );
    //System.out.println( "\tcreatePermissive = " + createPermissive );

    // See if a path was specified
    List<String> remainingArguments = commandLine.getArgList();
    String filePath = null;
    if( remainingArguments != null && remainingArguments.size() > 1 )
    {
      //System.out.println( "remainingArguments = " + remainingArguments );
      filePath = remainingArguments.get( 1 );
    }

    //System.out.println( "filePath = " + filePath );

    // The file path is required
    if( filePath == null || filePath.length() == 0 )
    {
      System.out.println( "usage: mkdir [-p] FILEPATH" );
      return;
    }

    INode desiredINode = currentDirectory;


    // TODO: need to implement a proper parsing of the file path
    //desiredINode = findINode( filePath, currentDirectory );

    int lastSlashIndex = filePath.lastIndexOf( '/' );
    String directoryName;
    if( lastSlashIndex < 0 )
    {
      directoryName = filePath;
    }
    else
    {
      directoryName = filePath.substring( lastSlashIndex + 1 );
    }

    //System.out.println( "Looking for directory with name " + directoryName );

    // Sanity check
    if( directoryName == null || directoryName.length() <= 0 )
    {
      System.out.println( "The file name cannot be empty" );
      return;
    }

    // Loop over all the directory entries referring to this INode so see if the directory already exists.
    // Get a list of the files in the directory
    List<FileDescriptor> fileDescriptors = getDirectoryContents( desiredINode );
    if( fileDescriptors != null && fileDescriptors.size() > 0 )
    {
      for( FileDescriptor fileDescriptor : fileDescriptors )
      {
        if( directoryName.equals( fileDescriptor.getName() ) )
        {
          System.out.println( "A file with name " + directoryName + " already exists in the specified directory" );
          return;
        }
      }
    }

    // Allocate a new INode as a directory
    int nextFreeINodeIndex = INodeMap.getNodeMap().nextClearBit(0 );

    // Sanity check
    if( nextFreeINodeIndex < 0 )
    {
      throw new OutOfINodesException( "The filesystem is out of free INodes" );
    }

    // Mark this INode as in use
    INodeMap.getNodeMap().set( nextFreeINodeIndex );

    // Allocate a directory data block for it
    int nextFreeDataBlockIndex = dataBlockMap.getNodeMap().nextClearBit( 0 );
    if( nextFreeDataBlockIndex < 0 )
    {
      throw new OutOfDataBlocksException( "There are no more free data blocks" );
    }

    // Mark this data block as in use
    dataBlockMap.getNodeMap().set( nextFreeDataBlockIndex );

    boolean INodeMapUpdated = true;
    boolean dataMapUpdated  = true;

    // Make up the new INode
    //System.out.println( "Creating INode " + nextFreeINodeIndex + " as new directory" );
    INode newINode = new INode( nextFreeINodeIndex );
    newINode.setAccessMode( AccessConstants.read | AccessConstants.write | AccessConstants.execute );
    newINode.setNumberOfLinks( 1 );
    newINode.setFileSize( FSConstants.BLOCK_SIZE );
    newINode.setType( INodeType.Directory );
    newINode.addDirectPointer( nextFreeDataBlockIndex + addressOfFirstDataBlock );
    newINode.setAllocatedBlocks( 1 );
    INodes[ nextFreeINodeIndex ] = newINode;

    // Make up the corresponding directory data block.
    DirectoryDataBlock newDataBlock = new DirectoryDataBlock();

    if( newDataBlock.createDirectoryEntry( newINode.getINodeNumber(), "." ) )
    {
      if( newDataBlock.createDirectoryEntry( desiredINode.getINodeNumber(), ".." ) )
      {
        dataBlocks[ nextFreeDataBlockIndex ] = newDataBlock;
      }
    }

    int newINodeCreated           = nextFreeINodeIndex;
    int directoryDataBlockCreated = nextFreeDataBlockIndex;

    // Try to write a new directory entry into one of the original INode's direct blocks
    boolean entryCreated                                 = false;
    DirectoryDataBlock  updatedDirectoryDataBlock        = null;
    int                 updatedDirectoryDataBlockPointer = -1;
    boolean             desiredINodeUpdated              = false;
    int[] directPointers = desiredINode.getDirectPointers();
    for( int i = 0; i < directPointers.length; i++ )
    {
      int directPointer = desiredINode.getDirectPointer( i );
      if( directPointer >= addressOfFirstDataBlock )
      {
        DirectoryDataBlock dataBlock = (DirectoryDataBlock)dataBlocks[directPointer - addressOfFirstDataBlock];
        if( dataBlock == null ) continue;

        // We got a valid directory block.  Try to write our pointer to it.
        if( dataBlock.createDirectoryEntry( newINode.getINodeNumber(), directoryName ) )
        {
          entryCreated              = true;
          updatedDirectoryDataBlockPointer = directPointer;
          updatedDirectoryDataBlock        = dataBlock;
          break;
        }
      }
      else
      {
        // Add a new DirectoryDataBlock
        DirectoryDataBlock dataBlock = new DirectoryDataBlock();

        // We need to find an unused data block
        nextFreeDataBlockIndex = dataBlockMap.getNodeMap().nextClearBit(0 );

        // Sanity check
        if( nextFreeDataBlockIndex < 0 )
        {
          throw new OutOfDataBlocksException( "The filesystem is out of free data blocks" );
        }

        // Mark this data block as in use
        dataBlockMap.getNodeMap().set( nextFreeDataBlockIndex );

        // Add the new data block to the list of data blocks
        directPointer = nextFreeDataBlockIndex + addressOfFirstDataBlock;
        dataBlocks[ nextFreeDataBlockIndex ] = dataBlock;
        desiredINode.setDirectPointer( i, directPointer );

        //  This next block of code should always work, but we'll leave the 'if' anyway.
        if( dataBlock.createDirectoryEntry( newINode.getINodeNumber(), directoryName ) )
        {
          entryCreated              = true;
          updatedDirectoryDataBlockPointer = directPointer;
          updatedDirectoryDataBlock        = dataBlock;
          desiredINodeUpdated              = true;
          break;
        }
      }
    }

    // Check if we successfully created the directory entry
    if( !entryCreated )
    {
      // TODO: allocate a new block from the indirect block
    }

    // Now we need to synchronize these changes with the underlying file system.
    // Write out the INodeMap
    inputFile.seek( addressOfINodeMap * FSConstants.BLOCK_SIZE );
    WriteNodeMap.write( inputFile, INodeMap );

    // Write out the dataBlockMap
    inputFile.seek( addressOfDataBlockMap * FSConstants.BLOCK_SIZE );
    WriteNodeMap.write( inputFile, dataBlockMap );

    // Write out the new INode containing the new directory
    inputFile.seek( addressOfFirstINode*FSConstants.BLOCK_SIZE + newINodeCreated*FSConstants.BYTES_PER_INODE );
    WriteINode.write( inputFile, newINode );

    // Write out the new directory data block
    inputFile.seek( ( addressOfFirstDataBlock + directoryDataBlockCreated )*FSConstants.BLOCK_SIZE );
    WriteDataBlock.write( inputFile, newDataBlock );

    // Write out the updated directory data block
    inputFile.seek( updatedDirectoryDataBlockPointer * FSConstants.BLOCK_SIZE );
    WriteDataBlock.write( inputFile, updatedDirectoryDataBlock );

    // Finally check if the original INode was updated --- through the addition of a new data block pointer
    if( desiredINodeUpdated )
    {
      inputFile.seek( addressOfFirstINode*FSConstants.BLOCK_SIZE + desiredINode.getINodeNumber()*FSConstants.BYTES_PER_INODE );
      WriteINode.write( inputFile, desiredINode );
    }
  }

  private boolean isEmpty( INode directoryINode ) throws NotADirectoryException
  {
    // Sanity check
    if( directoryINode.getType() != INodeType.Directory )
    {
      throw new NotADirectoryException( "The given INode " + directoryINode.getINodeNumber() + " is not a directory! ");
    }

    boolean empty = true;

    // Loop over the Direct Pointers first
    for( int directPointer : directoryINode.getDirectPointers() )
    {
      if( directPointer < addressOfFirstDataBlock ) continue;

      DirectoryDataBlock dataBlock = (DirectoryDataBlock)dataBlocks[directPointer - addressOfFirstDataBlock];

      // Get the Directory Entries
      for( DirectoryDataBlock.DirectoryEntry directoryEntry : dataBlock.getDirectoryEntries() )
      {
        int INodeNumber = directoryEntry.getINodeNumber();
        String fileName = directoryEntry.getName();

        if( INodeNumber < 0 ) continue;
        if( ".".equals( fileName.trim() ) || "..".equals( fileName.trim() ) ) continue;
        empty = false;
        break;
      }
      if( !empty ) break;
    }

    if( !empty )
    {
      return false;
    }

    // Now loop over the indirect entries
    int indirectBlockPointer = directoryINode.getIndirectPointer();
    if( indirectBlockPointer >= addressOfFirstDataBlock )
    {
      IndirectPointerBlock inDirectPointerBlock = (IndirectPointerBlock)dataBlocks[indirectBlockPointer - addressOfFirstDataBlock];
      for( int indirectPointer : inDirectPointerBlock.getIndirectPointers() )
      {

        if( indirectPointer < addressOfFirstDataBlock ) continue;

        DirectoryDataBlock dataBlock = (DirectoryDataBlock) dataBlocks[indirectPointer - addressOfFirstDataBlock];

        // Get the Directory Entries
        for( DirectoryDataBlock.DirectoryEntry directoryEntry : dataBlock.getDirectoryEntries() )
        {
          int INodeNumber = directoryEntry.getINodeNumber();
          String fileName = directoryEntry.getName();
          if( INodeNumber <= 0 ) continue;
          if( ".".equals( fileName.trim()) || "..".equals( fileName.trim() ) ) continue;
          empty = false;
          break;
        }
        if( !empty ) break;
      }
    }

    // That's it
    return empty;
  }


  private void removeHardlink( INode directoryINode, String fileName ) throws Exception
  {
    //System.out.println( "Removing hardlink for " + fileName );
    // Sanity check
    if( directoryINode.getType() != INodeType.Directory )
    {
      throw new NotADirectoryException( "The given INode " + directoryINode.getINodeNumber() + " is not a directory! " );
    }

    // Loop over all the links until we find the one in question
    boolean            linkRemoved                = false;
    int                containingDataBlockPointer = -1;
    DirectoryDataBlock containingDataBlock        = null;

    // Loop over the direct pointers first
    for( int directPointer : directoryINode.getDirectPointers() )
    {
      if( directPointer < addressOfFirstDataBlock ) continue;

      DirectoryDataBlock dataBlock = (DirectoryDataBlock) dataBlocks[directPointer - addressOfFirstDataBlock];

      // Get the Directory Entries
      for( DirectoryDataBlock.DirectoryEntry directoryEntry : dataBlock.getDirectoryEntries() )
      {
        if( directoryEntry.getName().trim().equals( fileName ) )
        {
          //System.out.println( "Found link in data block " + directPointer );
          containingDataBlockPointer = directPointer;
          containingDataBlock        = dataBlock;
          break;
        }
      }
    }

    // Check if we have found the data block
    if( containingDataBlock == null )
    {
      // Need to look through the indirect pointers
      int indirectBlockPointer = directoryINode.getIndirectPointer();
      if( indirectBlockPointer < addressOfFirstDataBlock )
      {
        throw new LinkNotFoundException( "Could not find link " + fileName + ", indirect block pointer is null" );
      }

      // Get the IndirectPointer block
      IndirectPointerBlock indirectPointerBlock = (IndirectPointerBlock)dataBlocks[ indirectBlockPointer ];
      if( indirectPointerBlock == null )
      {
        throw new LinkNotFoundException( "Could not find link " + fileName + ", indirect block is empty" );
      }

      // Loop over the indirect blocks
      for( int indirectPointer : indirectPointerBlock.getIndirectPointers() )
      {
        if( indirectPointer < addressOfFirstDataBlock ) continue;
        DirectoryDataBlock directoryDataBlock = (DirectoryDataBlock)dataBlocks[ indirectPointer ];

        // Get the Directory Entries
        for( DirectoryDataBlock.DirectoryEntry directoryEntry : directoryDataBlock.getDirectoryEntries() )
        {
          if( directoryEntry.getName().trim().equals( fileName ) )
          {
            containingDataBlockPointer = indirectPointer;
            containingDataBlock        = directoryDataBlock;
            break;
          }
        }
        if( containingDataBlock != null )
        {
          break;
        }
      }
    }

    // Check if we found the containing block
    if( containingDataBlock == null )
    {
      throw new LinkNotFoundException( "Could not find link for " + fileName );
    }

    // Remove the link
    containingDataBlock.removeDirectoryEntry( fileName );

    // Resynchronize the data block
    //System.out.println( "Synchronizing block " + containingDataBlockPointer );
    inputFile.seek( containingDataBlockPointer * FSConstants.BLOCK_SIZE );
    WriteDataBlock.write( inputFile, containingDataBlock );
  }

  // Remove a file INode and all its data blocks
  private void removeFile( INode fileINode ) throws Exception
  {
    // First remove all the data blocks
    //     Direct blocks first
    for( int directPointer : fileINode.getDirectPointers() )
    {
      if( directPointer >= addressOfFirstDataBlock )
      {
        int directIndex = directPointer - addressOfFirstDataBlock;
        dataBlockMap.getNodeMap().clear( directIndex );
        DataBlock newDataBlock = new DataBlock();
        dataBlocks[ directIndex ] = newDataBlock;
        inputFile.seek( directPointer * FSConstants.BLOCK_SIZE );
        WriteDataBlock.write( inputFile, newDataBlock );
      }
    }

    //     Now the indirect blocks, if any
    int indirectBlockPointer = fileINode.getIndirectPointer();
    if( indirectBlockPointer >= addressOfFirstDataBlock )
    {
      IndirectPointerBlock indirectPointerBlock = (IndirectPointerBlock)dataBlocks[ indirectBlockPointer - addressOfFirstDataBlock ];
      for( int indirectPointer : indirectPointerBlock.getIndirectPointers() )
      {
        if( indirectPointer >= addressOfFirstDataBlock )
        {
          int indirectIndex = indirectPointer - addressOfFirstDataBlock;
          dataBlockMap.getNodeMap().clear( indirectIndex );
          DataBlock newDataBlock = new DataBlock();
          dataBlocks[ indirectIndex ] = newDataBlock;
          inputFile.seek( indirectPointer * FSConstants.BLOCK_SIZE );
          WriteDataBlock.write( inputFile, newDataBlock );
        }
      }

      // Now remove the indirect pointer block itself
      int indirectBlockIndex = indirectBlockPointer - addressOfFirstDataBlock;
      dataBlockMap.getNodeMap().clear( indirectBlockIndex );

      DataBlock newDataBlock = new DataBlock();
      dataBlocks[ indirectBlockIndex ] = newDataBlock;
      inputFile.seek( indirectBlockPointer * FSConstants.BLOCK_SIZE );
      WriteDataBlock.write( inputFile, newDataBlock );
    }

    // Remove the given INode
    int INodeNumber = fileINode.getINodeNumber();
    INodeMap.getNodeMap().clear( INodeNumber );
    INode blankINode = new INode( 0 );
    INodes[ INodeNumber ] = blankINode;
    inputFile.seek( addressOfFirstINode*FSConstants.BLOCK_SIZE + INodeNumber*FSConstants.BYTES_PER_INODE );
    WriteINode.write( inputFile, blankINode );

    // Resynchronize the INode and DataBlock maps
    inputFile.seek( addressOfINodeMap * FSConstants.BLOCK_SIZE );
    WriteNodeMap.write( inputFile, INodeMap );
    inputFile.seek( addressOfDataBlockMap * FSConstants.BLOCK_SIZE );
    WriteNodeMap.write( inputFile, dataBlockMap );
  }

  // Remove a directory INode and all its data blocks
  private void removeDirectory( INode directoryINode, INode parentINode, String fileName ) throws Exception
  {
    // Remove all the entries in the directory
    List<FileDescriptor> fileDescriptors = getDirectoryContents( directoryINode );
    for( FileDescriptor fileDescriptor : fileDescriptors )
    {
      int    INodeNumber = fileDescriptor.getINodeNumber();
      String entryName   = fileDescriptor.getName();
      if( ".".equals( entryName.trim() ) || "..".equals( entryName.trim() ) ) continue;

      //System.out.println( "Entry is " + entryName );
      INode entryINode   = INodes[ INodeNumber ];
      if( entryINode.getType() == INodeType.File )
      {
        removeFile( entryINode );
        //removeHardlink( directoryINode, entryName );
      }
      else if( entryINode.getType() == INodeType.Directory )
      {
        if( isEmpty( entryINode ) )
        {
          //System.out.println( "This directory is empty" );
          //System.out.println( "Removing directory " + entryName );
          removeFile( entryINode );
          //removeHardlink( directoryINode, entryName );
        }
        else
        {
          //System.out.println( "This directory is not empty" );
          //System.out.println( "Removing directory for " + entryName );
          removeDirectory( entryINode, directoryINode, entryName );
          //removeHardlink( directoryINode, entryName );
        }

      }
      removeHardlink( directoryINode, entryName );
     }

     // The directory is now empty
    removeFile( directoryINode );
  }

  private List<FileDescriptor> getDirectoryContents( INode directoryINode ) throws NotADirectoryException
  {

    // Sanity check
    if( directoryINode.getType() != INodeType.Directory )
    {
      throw new NotADirectoryException( "The given INode " + directoryINode.getINodeNumber() + " is not a directory! ");
    }

    // Get a list of the files in the directory
    List<FileDescriptor> fileDescriptors = new ArrayList<>();

    // Loop over the Direct Pointers first
    for( int directPointer : directoryINode.getDirectPointers() )
    {
      //System.out.println( "directPointer = " + directPointer );
      if( directPointer < addressOfFirstDataBlock ) continue;

      DirectoryDataBlock dataBlock = (DirectoryDataBlock)dataBlocks[directPointer - addressOfFirstDataBlock];
      //System.out.println( "DirectoryDataBlock = " + dataBlock );
      //System.out.println( "Directory entries = " + dataBlock.getDirectoryEntries() );

      // Get the Directory Entries
      for( DirectoryDataBlock.DirectoryEntry directoryEntry : dataBlock.getDirectoryEntries() )
      {
        int INodeNumber = directoryEntry.getINodeNumber();
        String fileName = directoryEntry.getName();
        //System.out.println( "getDirectoryContents: INodeNumber = " + INodeNumber );
        //System.out.println( "getDirectoryContents: fileName = " + fileName );
        if( INodeNumber < 0 ) continue;

        FileDescriptor fileDescriptor = new FileDescriptor( fileName, INodeNumber );
        fileDescriptors.add( fileDescriptor );
      }
    }

    // Now loop over the indirect entries
    int indirectPointer = directoryINode.getIndirectPointer();
    if( indirectPointer >= addressOfFirstDataBlock )
    {
      // TODO: replace this with an IndirectPointerBlock
      DataBlock pointerBlock = dataBlocks[indirectPointer - addressOfFirstDataBlock];
      byte[] pointerData = pointerBlock.getData();
      for( int i = 0; i < FSConstants.BLOCK_SIZE; i += 4 )
      {
        int pointer = ByteBuffer.wrap( pointerData, i, 4 ).getInt();
        if( pointer <= addressOfFirstDataBlock ) break;

        DirectoryDataBlock dataBlock = (DirectoryDataBlock) dataBlocks[pointer - addressOfFirstDataBlock];

        // Get the Directory Entries
        for( DirectoryDataBlock.DirectoryEntry directoryEntry : dataBlock.getDirectoryEntries() )
        {
          int INodeNumber = directoryEntry.getINodeNumber();
          String fileName = directoryEntry.getName();
          if( INodeNumber <= 0 ) continue;

          FileDescriptor fileDescriptor = new FileDescriptor( fileName, INodeNumber );
          fileDescriptors.add( fileDescriptor );
        }
      }
    }

    // That's it
    return fileDescriptors;
  }


  private void handleLS( String[] args ) throws Exception
  {
    // Check if the parser is ready
    if( cliParser == null )
    {
      cliParser = new DefaultParser();
    }

    if( lsOptions == null )
    {
      lsOptions = new Options();
      lsOptions.addOption( "a", "all",  false, "do not hide entries starting with ." );
      lsOptions.addOption( "l", "long", false, "show extended information" );
    }

    // Parse the command line options.
    // The only possible arguments are -a, -l and combinations thereof
    boolean listLong = false;
    boolean listAll  = false;
    //System.out.println( "Parsing ls args: " );
    //for( String arg : args )
   // {
    //  System.out.println( "\t" + arg );
    //}
    CommandLine commandLine = cliParser.parse( lsOptions, args );
    if( commandLine.hasOption( 'a' ) ) listAll = true;
    if( commandLine.hasOption( 'l' ) ) listLong = true;

    //System.out.println( "For ls command, options are" );
    //System.out.println( "\tlistLong = " + listLong );
    //System.out.println( "\tlistAll = "  + listAll  );

    // See if a path was specified
    List<String> remainingArguments = commandLine.getArgList();
    String filePath = null;
    if( remainingArguments != null && remainingArguments.size() > 1 )
    {
      //System.out.println( "remainingArguments = " + remainingArguments );
      filePath = remainingArguments.get( 1 );
    }



    //System.out.println( "filePath = " + filePath );
    INode desiredINode = currentDirectory;
    if( filePath != null )
    {
      desiredINode = findINode( filePath, currentDirectory );
    }


    if( desiredINode == null )
    {
      System.out.println( "Could not find " + filePath );
      return;
    }
    // Check the file type
    if( desiredINode.getType() == INodeType.Directory )
    {
      //System.out.println( "Desired INode " + desiredINode.getINodeNumber() + " is a directory" );

      // Get a list of the files in the directory
      List<FileDescriptor> fileDescriptors = new ArrayList<>();

      // Loop over the Direct Pointers first
      for( int directPointer : desiredINode.getDirectPointers() )
      {
        //System.out.println( "directPointer = " + directPointer );
        if( directPointer < addressOfFirstDataBlock ) continue;

        DirectoryDataBlock dataBlock = (DirectoryDataBlock)dataBlocks[directPointer - addressOfFirstDataBlock];
        //System.out.println( "DirectoryDataBlock = " + dataBlock );
        //System.out.println( "Directory entries = " + dataBlock.getDirectoryEntries() );

        // Get the Directory Entries
        for( DirectoryDataBlock.DirectoryEntry directoryEntry : dataBlock.getDirectoryEntries() )
        {
          int INodeNumber = directoryEntry.getINodeNumber();
          String fileName = directoryEntry.getName();
          //System.out.println( "Shell: handleLS: INodeNumber = " + INodeNumber );
          //System.out.println( "Shell: handleLS: fileName = " + fileName );
          if( INodeNumber < 0 ) continue;

          FileDescriptor fileDescriptor = new FileDescriptor( fileName, INodeNumber );
          fileDescriptors.add( fileDescriptor );
        }
      }

      // Now loop over the indirect entries
      int indirectPointer = desiredINode.getIndirectPointer();
      if( indirectPointer >= addressOfFirstDataBlock )
      {
        IndirectPointerBlock pointerBlock = (IndirectPointerBlock)dataBlocks[indirectPointer - addressOfFirstDataBlock];
        //byte[] pointerData = pointerBlock.getData();
        //for( int i = 0; i < FSConstants.BLOCK_SIZE; i += 4 )
        for( int pointer : pointerBlock.getIndirectPointers() )
        {
          //int pointer = ByteBuffer.wrap( pointerData, i, 4 ).getInt();
          if( pointer <= addressOfFirstDataBlock ) break;

          DirectoryDataBlock dataBlock = (DirectoryDataBlock) dataBlocks[pointer - addressOfFirstDataBlock];

          // Get the Directory Entries
          for( DirectoryDataBlock.DirectoryEntry directoryEntry : dataBlock.getDirectoryEntries() )
          {
            int INodeNumber = directoryEntry.getINodeNumber();
            String fileName = directoryEntry.getName();
            if( INodeNumber <= 0 ) continue;

            FileDescriptor fileDescriptor = new FileDescriptor( fileName, INodeNumber );
            fileDescriptors.add( fileDescriptor );
          }
        }
      }

      // Finally report the results
      for( FileDescriptor fileDescriptor : fileDescriptors )
      {
        String name        = fileDescriptor.getName();
        int    INodeNumber = fileDescriptor.getINodeNumber();
        if( name.startsWith( "." ) )
        {
          if( !listAll ) continue;
        }

        if( !listLong )
        {
          System.out.println( name );
        }
        else
        {
          INode        currentINode = INodes[ INodeNumber ];
          /*
          StringBuffer accessBuffer = new StringBuffer();
          if( currentINode.getType() == INodeType.Directory )
          {
            accessBuffer.append( "d" );
          }
          else if( currentINode.getType() == INodeType.File )
          {
            accessBuffer.append( "-" );
          }
          else if( currentINode.getType() == INodeType.SymLink )
          {
            accessBuffer.append( "l" );
          }

          int accessMode = currentINode.getAccessMode();
          if( ( accessMode & AccessConstants.read ) != 0 )
          {
            accessBuffer.append( "r" );
          }
          else
          {
            accessBuffer.append( "-" );
          }

          if( ( accessMode & AccessConstants.write ) != 0 )
          {
            accessBuffer.append( "w" );
          }
          else
          {
            accessBuffer.append( "-" );
          }

          if( ( accessMode & AccessConstants.execute ) != 0 )
          {
            accessBuffer.append( "x" );
          }
          else
          {
            accessBuffer.append( "-" );
          }

          // Get the number of links
          int numberOfLinks = currentINode.getNumberOfLinks();
          accessBuffer.append( " " + numberOfLinks);

          // Get the file size
          int fileSize = currentINode.getFileSize();
          accessBuffer.append( " " + fileSize );

          accessBuffer.append( " " + name );
          String fileLine = accessBuffer.toString();
          */
          String fileLine = getLongListing( name, currentINode );

          // Print out everything
          System.out.println( fileLine );
        }
      }
    }
    else if( desiredINode.getType() == INodeType.File )
    {
      // Get just the name
      int lastSlashIndex = filePath.lastIndexOf( '/' );
      String name;
      if( lastSlashIndex >= 0 )
      {
        name = filePath.substring( lastSlashIndex + 1 );
      }
      else
      {
        name = filePath;
      }

      if( ".".equals( name ) || "..".equals( name ) )
      {
        if( !listAll )
        {
          return;
        }
      }

      if( listLong )
      {
        String fileLine = getLongListing( name, desiredINode );
        System.out.println( fileLine );
      }
      else
      {
        System.out.println( name );
      }
    }
  }

  private String getLongListing( String name, INode desiredINode )
  {
    //System.out.println( "getLongListing: getting listing for INode " + desiredINode.getINodeNumber() );
    StringBuffer listingBuffer = new StringBuffer();
    if( desiredINode.getType() == INodeType.Directory )
    {
      listingBuffer.append( "d" );
    }
    else if( desiredINode.getType() == INodeType.File )
    {
      listingBuffer.append( "-" );
    }
    else if( desiredINode.getType() == INodeType.SymLink )
    {
      listingBuffer.append( "l" );
    }

    int accessMode = desiredINode.getAccessMode();
    if( ( accessMode & AccessConstants.read ) != 0 )
    {
      listingBuffer.append( "r" );
    }
    else
    {
      listingBuffer.append( "-" );
    }

    if( ( accessMode & AccessConstants.write ) != 0 )
    {
      listingBuffer.append( "w" );
    }
    else
    {
      listingBuffer.append( "-" );
    }

    if( ( accessMode & AccessConstants.execute ) != 0 )
    {
      listingBuffer.append( "x" );
    }
    else
    {
      listingBuffer.append( "-" );
    }

    // Get the number of links
    int numberOfLinks = desiredINode.getNumberOfLinks();
    listingBuffer.append( " " + numberOfLinks);

    // Get the file size
    int fileSize = desiredINode.getFileSize();
    listingBuffer.append( " " + fileSize );

    listingBuffer.append( " " + name );
    String fileLine = listingBuffer.toString();

    return fileLine;
  }


  private INode findINode( String filePath, INode startingNode ) throws Exception
  {

    // Sanity check
    if( filePath == null || filePath.length() == 0 )
    {
      return startingNode;
    }

    // Check if this is a relative or an absolute path
    if( "/".equals( filePath ) )
    {
      return INodes[0];
    }
    else if( filePath.startsWith( "/" ) )
    {
      // This is an absolute path. Start from the root.
      startingNode = INodes[0];

      // Remove the leading '/' now
      filePath = filePath.substring( 1 );
    }

    // Sanity check: the starting INode may be an ordinary file.
    if( startingNode.getType() != INodeType.Directory )
    {
      return startingNode;
    }

    String[] fileComponents = filePath.split( "/" );
    INode currentNode = startingNode;

    for( String fileComponent : fileComponents )
    {
      // This should be in the current directory
      List<FileDescriptor> fileDescriptors = getDirectoryContents( currentNode );
      INode nextNode = null;
      for( FileDescriptor fileDescriptor : fileDescriptors )
      {
        if( fileDescriptor.getName().trim().equals( fileComponent ) )
        {
          nextNode = INodes[fileDescriptor.getINodeNumber()];
          break;
        }
      }

      //if( nextNode == null )
      //{
      //  throw new PathComponentNotFoundException( "Could not find " + fileComponent );
      // }

      currentNode = nextNode;
    }


    return currentNode;
  }

  public void close() throws IOException
  {
    inputFile.close();
  }



    public static void main( String[] args )
  {
    if( args.length != 1 )
    {
      System.out.println( "usage: Shell filePath" );
      System.exit( 1 );
    }

    // Get the path the file
    String filePath = args[0];
    File wcsuFile = new File( filePath );
    if( !wcsuFile.exists() )
    {
      System.out.println( "The file " + wcsuFile.getAbsolutePath() + " does not exist" );
      System.exit( 2 );
    }

    Shell shell = new Shell();

    try
    {
      // Try to mount the file
      shell.mount( wcsuFile );

      // Now issue interactive commands until
      shell.interactive();

      shell.close();
    }
    catch( Exception e )
    {
      System.out.println( "Caught exception: " + e );
      System.exit( 3 );
    }

  }
}
