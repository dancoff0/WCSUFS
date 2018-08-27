package edu.wcsu.wcsufs.FSDataStructures;

public class DataBlock
{
  // Member variables
  byte[] data;

  public DataBlock()
  {
    data = new byte[ FSConstants.BLOCK_SIZE ];
  }

  public void setData( byte[] newData )
  {
    this.data = new byte[ newData.length ];
    System.arraycopy( newData, 0, data, 0, newData.length );
  }

  public byte[] getData()
  {
    return data;
  }
}
