package edu.wcsu.wcsufs.FSDataStructures;

public enum INodeType
{

  Directory(1),
  File (2),
  SymLink (3),
  Unused(4);

  // Member data
  private final int type;

  INodeType( int type )
  {
    this.type = type;
  }

  public int getType()
  {
    return type;
  }
  public static INodeType lookUpType( int type )
  {
    // Loop over the current assigned types
    for( INodeType currentType: values() )
    {
      if( currentType.getType() == type )
      {
        return currentType;
      }
    }

    return INodeType.Unused;
  }
}
