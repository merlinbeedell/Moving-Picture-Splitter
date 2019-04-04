/*
Extract the static image and video from a motion picture taken with an android (e.g. Huawei) phone
  All the hard work was done by Trekky12 in python.  This is my attempt using "groovy" - a Java wrapper language. 
  https://github.com/Trekky12/moving-picture-extract  
  MGB April 2019
*/

def cli = new CliBuilder(usage:"""Split out the static image and video from a Motion Picture taken with an android (e.g. Huawei) phone.
The original jpg file(s) will be unchanged, and new .jpg and a .mp4 files will be created in the same directory.
This will only split Motion Picture files.
Pass in a single file or a directory of files.
""", footer:'\nMGB 2019.')
 cli.v(longOpt:'verbose',args:0, required: false, 'output extra progress info')
 cli.p(longOpt:'prefix',args:1, required: false,  'prefix for each extracted file\'s filename. No default')
 cli.s(longOpt:'suffix',args:1, required: false,  'suffix for each extracted file\'s filename. Default "_mp"')
 cli.o(longOpt:'overwrite',args:0, required: false, 'overwrite any files of the same name')
 cli.j(longOpt:'jpg',args:0, required: false, 'only output jpg')
 cli.m(longOpt:'mp4',args:0, required: false, 'only output mp4')
 cli.d(longOpt:'dir', args:1, required: false, 'Diretory path for the splitted files. Default is same directory as input.')
 cli.h('help')
 opts = cli.parse(args)

finputs = opts.arguments()
output = null
prefix = ""
suffix = "_mp"
overwrite = opts.o

if (opts.h || !finputs) {
    cli.usage()
    return;
    System.exit(3)
}
if (opts.j && opts.m) {
  println "Only set one of JPG (-j) or MP4 (-m)! Without either switch, both jpg and mp4 will be extacted."
  return
}
if (opts.d) {
  output = new File(opts.d)
  if (! (output.exists() && output.isDirectory())  ) {
    println "Output directory [${opts.d}] does not exist or is not a directory."
    return
  }
}
if (opts.p) {
  prefix = opts.p.trim()
  if (prefix.size() > 4) {
    println "Prefix value [$prefix] is too long. Max 4 characters"
    return
  }
}
if (opts.s) {
  suffix = opts.s.trim()
  if (suffix.size() > 4) {
    println "Suffix value [$suffix] is too long. Max 4 characters"
    return
  }
}

magicKey = "ctrace\u0000\u0000\u0000\u0000\u0000\u0018ftypmp42".getBytes()

finputs.each { finp ->
    finF = new File(finp)
    if (finF.exists())
    {
      if (finF.isDirectory())
      {
        finF.eachFileMatch (~/.*\.jpg/) {splitAFile(it)}
      } else {
        splitAFile (finF)
      }
    } else {
      println " ERR: $finp is not a directory or a file"
    }
}
//println magicKey

void splitAFile(jpgfile)
{
  
  min = 1 * 1024 * 1024 //1Mb
  if (opts.v) println "Processing file $jpgfile"
  fsize = jpgfile.size()
  if (fsize > 20 * 1024 * 1024) {
    if (opts.v) println " $fsize bytes is too big"
    return
  } else if (fsize < min) {
    if (opts.v) println " $fsize bytes is too small"
    return
  }
  //return
  //slightly lazy - load the whole file into a byte array in memory..
  byte[] contents = jpgfile.bytes
  //search for the 'magic byte sequence'
  // which will not be too near the start, and less than 1/3rd of the way through
  boolean possible = false
  for (i = min; i < fsize - min; i++)
  {
    
    if (contents[i] == magicKey[0]) {
      possible = true
      for (j=1; j<magicKey.size() && possible; j++)
      {
        possible = (contents[i+j] == magicKey[j])
      }
      if (possible) break;
    }
  }
  if (possible) // a match was found!
  {
    def filemodifiedtime = java.nio.file.Files.getLastModifiedTime(jpgfile.toPath())
    //def filecreatetime = java.nio.file.Files.getAttribute(jpgfile.toPath(), "creationTime")
    def filename = prefix + jpgfile.name.replace(".jpg", suffix)
    def filepath = (opts.d) ? output : jpgfile.parentFile
    if (!opts.m) {  //if not mp4 only
      def ojpg = new File(filepath,  filename + ".jpg")
      if (!overwrite && ojpg.exists()) {
          print " Output file ${ojpg.absolutePath} already exists.  Re-run with -o to overwite"
      } else {
        ojpg.withOutputStream { bout ->
          (0..i).each { aByte -> 
            bout.write(contents[aByte])
          }
        }
        // match the file date to the orginal
        //java.nio.file.Files.setAttribute(ojpg.toPath(), "creationTime", filecreatetime)
        java.nio.file.Files.setLastModifiedTime(ojpg.toPath(), filemodifiedtime)
      }
    }
    if (!opts.j) { // if not jpg only
      def omp4 = new File(filepath,  filename + ".mp4")

      if (!overwrite && omp4.exists()) {
          print " Output file ${ojpg.absolutePath} already exists.  Re-run with -o to overwite"
      } else {
        omp4.withOutputStream { bout ->
          //(i+8..fsize-1).each { aByte ->  //groovy style range loop.  or a nostalic old school loop..
          for (aByte = i+8; aByte < fsize; aByte ++) { 
            bout.write(contents[aByte])
          }
        }
        // match the file date to the orginal
        //java.nio.file.Files.setAttribute(omp4.toPath(), "creationTime", filecreatetime)
        java.nio.file.Files.setLastModifiedTime(omp4.toPath(), filemodifiedtime)

      }
    }

    if (opts.v) println "  found a match at byte $i out of $fsize"
  }
}

 /**** this is the same key parts in python script..
            try:
                ctrace_index = file_contents.index(
                    b'ctrace\x00\x00\x00\x00\x00\x18ftypmp42')
            except ValueError:
                if (options.v) println("JPG tail and MP4 head was not found in $jpgfile");

            if (optons.v) println("Splitting $jpgfile");
            split_index = ctrace_index + 8

            # Return to start, and copy data
            src.seek(0, 0)
            print(out_directory)
            // Reads and saves the static image data
            img = src.read(split_index)
            if opts.extract_jpg:
                with open(out_directory + filename + '-tiny.jpg',
                          'wb') as dst_jpg:
                    dst_jpg.write(img)
            // Reads and saves the mp4 video data
            mov = src.read()
            if opts.extract_mp4:
                with open(out_directory + filename + '.mp4', 'wb') as dst_mp4:
                    dst_mp4.write(mov)
*****/
