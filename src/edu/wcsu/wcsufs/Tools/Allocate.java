package edu.wcsu.wcsufs.Tools;


import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

public class Allocate
{
  public static void main( String[] args )
  {
    // Sanity check
    if( args.length != 2 )
    {
      System.out.println( "usage: Allocate path size" );
      System.out.println( "args.length = " + args.length );
      for( String arg : args )
      {
        System.out.print( "arg = " + arg );
      }
      System.exit(1 );
    }

    // Get the path
    String filePath = args[0];
    File file = new File( filePath );

    // First sanity check: make sure the does not already exist
    if( file.exists() )
    {
      System.out.println( "The file " + filePath + " already exists" );
      System.exit( 2 );
    }

    // Sanity check two: Try to create the file
    try
    {
      file.createNewFile();
    }
    catch( IOException ioe )
    {
      System.out.println( "Could not create file: " + ioe );
      System.exit( 3 );
    }

    // Get the file size
    String fileSizeString = args[1].trim();
    int fileSize = -1;
    if( fileSizeString.endsWith( "K" ) )
    {
      fileSize = Integer.parseInt( fileSizeString.substring(0, fileSizeString.length() - 1) ) * 1024;
    }
    else if( fileSizeString.endsWith( "M" ) )
    {
      fileSize = Integer.parseInt( fileSizeString.substring(0, fileSizeString.length() - 1) ) * 1048576;
    }
    else
    {
      fileSize = Integer.parseInt( fileSizeString );
    }

    if( fileSize <= 0 )
    {
      System.out.println( "The specified file size " + fileSizeString + " is illegal" );
    }
    System.out.println( "Allocating " + fileSizeString + " bytes to " + filePath );

    // Pad the file out to the desired size by writing zeros to it.
    try
    {
      FileOutputStream fileWriter = new FileOutputStream( file );
      for( int i = 0; i < fileSize; i++ )
      {
        fileWriter.write( 0 );
      }
      fileWriter.close();
    }
    catch( IOException ioe )
    {
      System.out.println( "Caught exception writing to new file " +  ioe );
    }
    System.out.println( "File " + filePath + " is now ready for formatting" );
  }
}
