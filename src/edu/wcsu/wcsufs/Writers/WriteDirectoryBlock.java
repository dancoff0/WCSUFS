package edu.wcsu.wcsufs.Writers;

import edu.wcsu.wcsufs.FSDataStructures.DirectoryDataBlock;
import edu.wcsu.wcsufs.FSDataStructures.FSConstants;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class WriteDirectoryBlock
{
  public static boolean write( RandomAccessFile file, DirectoryDataBlock dataBlock ) throws IOException
  {
    // Loop over the DirectoryEntries
    ArrayList<DirectoryDataBlock.DirectoryEntry> directoryEntries = dataBlock.getDirectoryEntries();
    int remainingBytes = FSConstants.BLOCK_SIZE;

    if( directoryEntries != null && directoryEntries.size() > 0 )
    {
      for( DirectoryDataBlock.DirectoryEntry directoryEntry : directoryEntries )
      {
        int    inodeNumber  = directoryEntry.getINodeNumber();
        int    recordLength = directoryEntry.getRecordLength();
        int    nameLength   = directoryEntry.getNameLength();
        String name         = directoryEntry.getName();
        int padding = recordLength -
            (     DirectoryDataBlock.DirectoryEntry.INODE_NUMBER_BYTES
                + DirectoryDataBlock.DirectoryEntry.RECORD_LENGTH_BYTES
                + DirectoryDataBlock.DirectoryEntry.NAME_LENGTH_BYTES
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
        byte[] data = ByteBuffer.allocate(4).putInt( inodeNumber ).array();
        file.write( data, 0,  DirectoryDataBlock.DirectoryEntry.INODE_NUMBER_BYTES );
        //offset +=  DirectoryDataBlock.DirectoryEntry.INODE_NUMBER_BYTES;

        // Write out the Record Length
        data = ByteBuffer.allocate(2).putShort( (short)recordLength ).array();
        file.write( data, 0,  DirectoryDataBlock.DirectoryEntry.RECORD_LENGTH_BYTES );
        //offset +=  DirectoryDataBlock.DirectoryEntry.RECORD_LENGTH_BYTES;

        // Write out the name length
        data = ByteBuffer.allocate(2).putShort( (short)nameLength ).array();
        file.write( data, 0,  DirectoryDataBlock.DirectoryEntry.NAME_LENGTH_BYTES );
        //offset +=  DirectoryDataBlock.DirectoryEntry.NAME_LENGTH_BYTES;

        // Write out the name
        data = name.getBytes();
        file.write( data, 0,  data.length);
        //offset +=  data.length;

        // Write out a terminating '\0' any additional padding needed.
        data = new byte[ 1 + padding ];
        data[0] = '\0';
        file.write( data, 0, 1 + padding );
        //offset += 1 + padding;

        // Decrease the remaining bytes by the length of this record.
        remainingBytes -= recordLength;
      }
    }

    // See if there are any remaining bytes
    if( remainingBytes > 0 )
    {
      byte[] filler = new byte[ remainingBytes ];
      file.write( filler, 0, remainingBytes );
    }

    // That's it
    return true;
  }
}
