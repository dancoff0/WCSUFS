package edu.wcsu.wcsufs.FSDataStructures;

public class FileDescriptor
{
  // Member data
  private final String name;
  private final int    INodeNumber;

  public FileDescriptor(String name, int INodeNumber )
  {
    this.name        = name;
    this.INodeNumber = INodeNumber;
  }

  public String getName()
  {
    return name;
  }

  public int getINodeNumber()
  {
    return INodeNumber;
  }
}
