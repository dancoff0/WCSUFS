package edu.wcsu.wcsufs.FSDataStructures;

import java.util.BitSet;

public class NodeMap
{
  // Member data
  private final NodeMapType type;
  private BitSet            nodeMap;

  // Constructor
  public NodeMap( NodeMapType type, int numberOfBits )
  {
    this.type    = type;
    this.nodeMap = new BitSet( numberOfBits );
  }

  public void setNodeAllocation( boolean allocated, int nodeNumber )
  {
    assert nodeNumber >= 0 && nodeNumber < nodeMap.size();
    if( allocated )
    {
      nodeMap.set( nodeNumber );
    }
    else
    {
      nodeMap.clear( nodeNumber );
    }
  }

  public boolean isNodeAllocated( int nodeNumber )
  {
    assert nodeNumber > 0 && nodeNumber < nodeMap.size();
    return nodeMap.get( nodeNumber );
  }

  public int getFirstUnallocatedBlock()
  {
    return nodeMap.nextClearBit( 0 );
  }

  public BitSet getNodeMap()
  {
    return nodeMap;
  }
}
