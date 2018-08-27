package edu.wcsu.wcsufs.FSDataStructures;

import edu.wcsu.wcsufs.Exceptions.CreateDirectoryEntryException;
import edu.wcsu.wcsufs.Exceptions.DirectoryEmptyException;
import edu.wcsu.wcsufs.Exceptions.DirectoryEntryNotFoundException;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class DirectoryDataBlock extends DataBlock
{
  // Member data
  private int allocatedBytes;
  private ArrayList<DirectoryEntry> directoryEntries;

  public DirectoryDataBlock()
  {
    allocatedBytes   = 0;
    directoryEntries = null;
  }

  @Override
  public void setData( byte[] data )
  {
    // First set the data in the block
    super.setData( data );

    // Now parse it into Directory Entry blocks
    int offset = 0;
    while( offset < FSConstants.BLOCK_SIZE )
    {
      int offsetSave = offset;

      // Get the INode number
      int INodeNumber = ByteBuffer.wrap( data, offset, DirectoryEntry.INODE_NUMBER_BYTES ).getInt();
      offset += DirectoryEntry.INODE_NUMBER_BYTES;

      // Get the record length
      short recordLength = ByteBuffer.wrap( data, offset, DirectoryEntry.RECORD_LENGTH_BYTES ).getShort();
      offset += DirectoryEntry.RECORD_LENGTH_BYTES;

      // Check if this record has been deallocated
      if( INodeNumber < 0 )
      {
        // Need to skip this record
        offset += recordLength;
        continue;
      }

      // Check if this is just blank space
      if( recordLength == 0 )
      {
        break;
      }

      // Get the name length
      short nameLength = ByteBuffer.wrap( data, offset, DirectoryEntry.NAME_LENGTH_BYTES ).getShort();
      offset += DirectoryEntry.NAME_LENGTH_BYTES;

      // Get the actual name
      byte[] nameBytes = new byte[ nameLength ];
      System.arraycopy( data, offset, nameBytes, 0, nameLength - 1 );
      nameBytes[ nameBytes.length - 1 ] = '\0';
      String fileName = new String( nameBytes );

      /*
      System.out.println( "DirectoryDataBlock: Recreating entry " + fileName + "( "+ INodeNumber + " )"
             + ", nameLength = " + nameLength + ", recordLength = " + recordLength );
             */
      DirectoryEntry newDirectoryEntry = new DirectoryEntry( INodeNumber, fileName );
      if( directoryEntries == null )
      {
        directoryEntries = new ArrayList<>();
      }
      directoryEntries.add( newDirectoryEntry );

      // That's it for this one
      offset = offsetSave + recordLength;
    }
  }

  @Override
  public byte[] getData()
  {
    // Check if there are any directory entries --- there always should be at least one.
    if( directoryEntries == null || directoryEntries.size() == 0 )
    {
      return data;
    }

    int remainingBytes = FSConstants.BLOCK_SIZE;
    allocatedBytes = 0;

    // Now loop over the directory entries, writing them in compressed fashion to the data block.
    int dataOffset = 0;
    for( DirectoryEntry directoryEntry : directoryEntries )
    {
      int    inodeNumber  = directoryEntry.getINodeNumber();
      int    recordLength = directoryEntry.getRecordLength();
      int    nameLength   = directoryEntry.getNameLength();
      String name         = directoryEntry.getName();
      int padding = recordLength -
          (     DirectoryEntry.INODE_NUMBER_BYTES
              + DirectoryEntry.RECORD_LENGTH_BYTES
              + DirectoryEntry.NAME_LENGTH_BYTES
              + nameLength );

      /*
      System.out.println( "DirectoryDataBlock: writing out entry: " );
      System.out.println( "\tname = " + name );
      System.out.println( "\tINodeNumber = " + inodeNumber );
      System.out.println( "\tnameLength = " + nameLength );
      System.out.println( "\trecordLength = " + recordLength );
      System.out.println( "\tpadding = " + padding );
      */

      // Write out the INode number
      byte[] temp = ByteBuffer.allocate(DirectoryEntry.INODE_NUMBER_BYTES ).putInt( inodeNumber ).array();
      System.arraycopy( temp,0, data, dataOffset, DirectoryEntry.INODE_NUMBER_BYTES );
      dataOffset +=  DirectoryEntry.INODE_NUMBER_BYTES;

      // Write out the Record Length
      temp = ByteBuffer.allocate(DirectoryEntry.RECORD_LENGTH_BYTES ).putShort( (short)recordLength ).array();
      System.arraycopy( temp, 0, data, dataOffset,  DirectoryEntry.RECORD_LENGTH_BYTES );
      dataOffset +=  DirectoryEntry.RECORD_LENGTH_BYTES;

      // Write out the name length
      temp = ByteBuffer.allocate(DirectoryEntry.NAME_LENGTH_BYTES).putShort( (short)nameLength ).array();
      System.arraycopy( temp, 0,  data, dataOffset, DirectoryEntry.NAME_LENGTH_BYTES );
      dataOffset +=  DirectoryEntry.NAME_LENGTH_BYTES;

      // Write out the name
      temp = name.getBytes();
      System.arraycopy( temp, 0, data, dataOffset,  temp.length);
      dataOffset +=  temp.length;

      // Write out a terminating '\0' if needed
      if( !name.endsWith( "\0" ) )
      {
        temp = new byte[1];
        temp[0] = '\0';
        System.arraycopy( temp, 0, data, dataOffset, 1 );
        dataOffset += 1;
      }
      // Now any additional padding needed.
      temp = new byte[  padding ];
      System.arraycopy( temp, 0, data, dataOffset,  padding );
      dataOffset += padding;

      // Keep track of the number of allocated bytes
      allocatedBytes += recordLength;

      // Decrease the remaining bytes by the length of this record.
      remainingBytes -= recordLength;
    }


    // See if there are any remaining bytes
    if( remainingBytes > 0 )
    {
      byte[] filler = new byte[ remainingBytes ];
      System.arraycopy( filler, 0, data, dataOffset, remainingBytes );
    }

    return data;
  }

  public boolean createDirectoryEntry( int INodeNumber, String name ) throws CreateDirectoryEntryException
  {
    // Sanity check: make sure the name is not already in use
    if( directoryEntries != null )
    {
      for( DirectoryEntry currentEntry : directoryEntries )
      {
        if( currentEntry.getName().equals( name ) )
        {
          throw new CreateDirectoryEntryException( "The name " + name + " is already in use" );
        }
      }
    }

    DirectoryEntry newEntry = new DirectoryEntry( INodeNumber, name );

    //  Check the total size of the record
    if( newEntry.getRecordLength() + allocatedBytes < FSConstants.BLOCK_SIZE )
    {
      // Add the entry to the list and update the number of allocated bytes
      if( directoryEntries == null ) directoryEntries = new ArrayList<>();
      directoryEntries.add( newEntry );
      allocatedBytes += newEntry.getRecordLength();
      return true;
    }
    else
    {
      return false;
    }
  }

  public void removeDirectoryEntry( String name ) throws DirectoryEmptyException, DirectoryEntryNotFoundException
  {
    //System.out.println( "DirectoryDataBlock: removeDirectoryEntry: directoryEntries = " + directoryEntries );
    if( directoryEntries == null || directoryEntries.size() <= 1 )
    {
      throw new DirectoryEmptyException( "The current directory is empty" );
    }

    boolean entryFound = false;
    for( int i = 0; i < directoryEntries.size(); i++ )
    {
      DirectoryEntry currentEntry = directoryEntries.get( i );
      if( currentEntry.getName().trim().equals( name ) )
      {
        allocatedBytes -= currentEntry.getRecordLength();
        directoryEntries.remove( i );
        entryFound = true;
        break;
      }
    }
    if( !entryFound )
    {
      throw new DirectoryEntryNotFoundException( "Could not find an entry for " + name );
    }
  }

  public ArrayList<DirectoryEntry> getDirectoryEntries()
  {
    return directoryEntries;
  }

  public static class DirectoryEntry
  {
    // Member data
    private int    INodeNumber;
    private int    recordLength;
    private int    nameLength;
    private String name;

    // Constants
    public static final int INODE_NUMBER_BYTES  = 4;
    public static final int RECORD_LENGTH_BYTES = 2;
    public static final int NAME_LENGTH_BYTES   = 2;

    // Constructor
    public DirectoryEntry( int INodeNumber, String name )
    {
      this.INodeNumber = INodeNumber;
      this.name        = name;
      this.nameLength  = name.length();
      if( !name.endsWith( "\0" ))
      {
        this.nameLength += 1;// Need to remember to allocate space for the '\0'
      }
      //System.out.println( "DirectoryEntry: namelength = " + nameLength + " for " + name );
      // Compute the space needed for the name, rounded up to the nearest 4
      int spaceForName =  ((4 + nameLength - 1)/ 4) * 4;
      this.recordLength = INODE_NUMBER_BYTES + RECORD_LENGTH_BYTES + NAME_LENGTH_BYTES + spaceForName;
    }

    // Accessors
    public int getINodeNumber()
    {
      return INodeNumber;
    }

    public int getRecordLength()
    {
      return recordLength;
    }

    public int getNameLength()
    {
      return nameLength;
    }

    public String getName()
    {
      return name;
    }

    public void setName( String name )
    {
      this.name = name;
      this.nameLength  = name.length() + 1; // Need to remember to allocate space for the '\0'
      // Compute the space needed for the name, rounded up to the nearest 4
      int spaceForName =  ((4 + nameLength - 1)/ 4) * 4;
      this.recordLength = INODE_NUMBER_BYTES + RECORD_LENGTH_BYTES + NAME_LENGTH_BYTES + spaceForName;
    }
  }
}
