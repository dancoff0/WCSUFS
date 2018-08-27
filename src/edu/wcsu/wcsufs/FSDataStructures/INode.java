package edu.wcsu.wcsufs.FSDataStructures;

public class INode
{
  // Member data
  private final int INodeNumber;
  private int       fileSize;
  private int       allocatedBlocks;
  private INodeType type;
  private int       numberOfLinks;
  private int       accessMode;

  private int[]     directPointers;
  private int       indirectPointer;

  // Constants
  public static final int NUMBER_OF_DIRECT_POINTERS = 4;

  private int lastUsedPointer;

  public INode( int INodeNumber )
  {
    this.INodeNumber = INodeNumber;
    allocatedBlocks = 0;
    numberOfLinks   = 0;
    accessMode      = 0;
    type            = INodeType.Unused;

    // Initialize the data pointers
    directPointers = new int[ NUMBER_OF_DIRECT_POINTERS ];
    for( int directPointer : directPointers )
    {
      directPointer = 0;
    }
    lastUsedPointer = -1;

    indirectPointer  = 0;
  }

  // Accessors
  public int getINodeNumber()
  {
    return INodeNumber;
  }

  public void setFileSize( int fileSize )
  {
    this.fileSize = fileSize;
  }

  public int getFileSize()
  {
    return fileSize;
  }

  public void setAllocatedBlocks( int allocatedBlocks )
  {
    this.allocatedBlocks = allocatedBlocks;
  }

  public int getAllocatedBlocks()
  {
    return allocatedBlocks;
  }

  public void setType( INodeType type )
  {
    this.type = type;
  }

  public INodeType getType()
  {
    return type;
  }

  public void setNumberOfLinks( int numberOfLinks )
  {
    this.numberOfLinks = numberOfLinks;
  }

  public int getNumberOfLinks()
  {
    return numberOfLinks;
  }

  public void setAccessMode( int accessMode )
  {
    this.accessMode = accessMode;
  }

  public int getAccessMode()
  {
    return accessMode;
  }

  public void setIndirectPointer( int indirectPointer )
  {
    this.indirectPointer = indirectPointer;
  }

  public int getIndirectPointer()
  {
    return indirectPointer;
  }

  public boolean addDirectPointer( int directPointer )
  {
    boolean success = false;
    if( lastUsedPointer + 1 < NUMBER_OF_DIRECT_POINTERS )
    {
      lastUsedPointer++;
      directPointers[lastUsedPointer] = directPointer;
      allocatedBlocks++;
      success = true;
    }
    return success;
  }

  public void setDirectPointer( int index, int directPointer )
  {
    assert index >= 0 && index < NUMBER_OF_DIRECT_POINTERS;
    directPointers[ index ] = directPointer;
  }

  public int getDirectPointer( int index )
  {
    assert index >= 0 && index < NUMBER_OF_DIRECT_POINTERS;
    return directPointers[ index ];
  }

  public void setDirectPointers( int[] directPointers )
  {
    this.directPointers = directPointers;
  }

  public int[] getDirectPointers()
  {
    return directPointers;
  }

}
