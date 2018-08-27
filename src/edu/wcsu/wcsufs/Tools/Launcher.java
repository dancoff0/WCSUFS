package edu.wcsu.wcsufs.Tools;

public class Launcher
{
  /**
   * @param args
   */
  public static void main(String[] args) throws Exception
  {
    if (args != null && args.length > 0)
    {
      String option = args[0];
      String[] args2 = new String[0];

      if( args.length > 1)
      {
        args2 = new String[ args.length-1 ];
        System.arraycopy(args, 1, args2, 0, args2.length);
      }

      if(option.equals("Allocate"))
      {
        Allocate.main( args2 );
      }
      else if( option.equals( "Format" ) )
      {
        Format.main( args2 );
      }
      else if( option.equals( "Shell" ) )
      {
        Shell.main( args2 );
      }



    }
  }
}