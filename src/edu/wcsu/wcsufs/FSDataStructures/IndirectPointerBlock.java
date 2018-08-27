package edu.wcsu.wcsufs.FSDataStructures;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class IndirectPointerBlock extends DataBlock
{
  // Member data
  private List<Integer> indirectPointers;
  private final int     addressOfFirstDataBlock;

  public IndirectPointerBlock( int addressOfFirstDataBlock )
  {
    indirectPointers = new ArrayList<>();
    this.addressOfFirstDataBlock = addressOfFirstDataBlock;
  }

  @Override
  public void setData( byte[] data )
  {
    super.setData( data );

    // Create the array of indirect pointers
    for( int i = 0; i < data.length; i += 4 )
    {
      int indirectPointer = ByteBuffer.wrap( data, i, 4 ).getInt();
      if( indirectPointer < addressOfFirstDataBlock )
      {
        break;
      }

      indirectPointers.add( indirectPointer );
    }
  }

  @Override
  public byte[] getData()
  {
    byte[] temp = new byte[ FSConstants.BLOCK_SIZE ];

    // Loop over the indirect pointers
    byte[] tempData;
    int pointerOffset = 0;
    for( int indirectPointer: indirectPointers )
    {
      tempData = ByteBuffer.allocate( 4 ).putInt( indirectPointer ).array();
      System.arraycopy( tempData, 0, temp,  pointerOffset, 4 );
      pointerOffset += 4;
    }

    return temp;
  }

  public boolean addIndirectPointer( int indirectPointer )
  {
    if( indirectPointers.size() - 1 < FSConstants.BLOCK_SIZE / 4 )
    {
      indirectPointers.add( indirectPointer );
      return true;
    }
    else
    {
      return false;
    }
  }

  public List<Integer> getIndirectPointers()
  {
    return indirectPointers;
  }

}
