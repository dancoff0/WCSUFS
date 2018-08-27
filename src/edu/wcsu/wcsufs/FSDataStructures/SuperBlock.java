package edu.wcsu.wcsufs.FSDataStructures;

public class SuperBlock
{
  // Constants
  public static int MAGIC = 0x57435355;   // WCSU in ASCII

  // Member data
  private int magic;
  private int numberOfINodes;
  private int numberOfDataBlocks;
  private int addressOfINodeMap;
  private int addressOfDataBlockMap;
  private int addressOfFirstInode;
  private int addressOfFirstDataBlock;
  private int totalBlocks;

  public SuperBlock()
  {
  }

  // Accessors
  public void setMagic( int magic )
  {
    this.magic = magic;
  }

  public int getMagic()
  {
    return magic;
  }

  public void setNumberOfINodes( int numberOfINodes )
  {
    this.numberOfINodes = numberOfINodes;
  }

  public int getNumberOfINodes()
  {
    return numberOfINodes;
  }

  public void setNumberOfDataBlocks( int numberOfDataBlocks )
  {
    this.numberOfDataBlocks = numberOfDataBlocks;
  }

  public int getNumberOfDataBlocks()
  {
    return numberOfDataBlocks;
  }

  public void setAddressOfINodeMap( int addressOfINodeMap )
  {
    this.addressOfINodeMap = addressOfINodeMap;
  }

  public int getAddressOfINodeMap()
  {
    return addressOfINodeMap;
  }

  public void setAddressOfDataBlockMap( int addressOfDataBlockMap )
  {
    this.addressOfDataBlockMap = addressOfDataBlockMap;
  }

  public int getAddressOfDataBlockMap()
  {
    return addressOfDataBlockMap;
  }

  public void setAddressOfFirstInode( int addressOfFirstInode )
  {
    this.addressOfFirstInode = addressOfFirstInode;
  }

  public int getAddressOfFirstInode()
  {
    return addressOfFirstInode;
  }

  public void setAddressOfFirstDataBlock( int addressOfFirstDataBlock )
  {
    this.addressOfFirstDataBlock = addressOfFirstDataBlock;
  }

  public int getAddressOfFirstDataBlock()
  {
    return addressOfFirstDataBlock;
  }

  public void setTotalBlocks( int totalBlocks )
  {
    this.totalBlocks = totalBlocks;
  }

  public int getTotalBlocks()
  {
    return totalBlocks;
  }
}


