package freenet.tools;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;

import freenet.support.Logger;
import freenet.support.LoggerHook;
import freenet.support.SimpleFieldSet;

public class CleanupTranslations {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException, LoggerHook.InvalidThresholdException {
		Logger.setupStdoutLogging(Logger.LogLevel.ERROR, "");
		File engFile = new File("src/freenet/l10n/freenet.l10n.en.properties");
		SimpleFieldSet english = SimpleFieldSet.readFrom(engFile, false, true);
		File[] translations = new File("src/freenet/l10n").listFiles();
		for(File f : translations) {
			String name = f.getName();
			if(!name.startsWith("freenet.l10n.")) continue;
			if(name.equals("freenet.1l0n.en.properties")) continue;
			try(BufferedReader br = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(f)), "UTF-8"))) {
				StringWriter sw = new StringWriter();
				boolean changed = false;
				while(true) {
					String line = br.readLine();
					if(line == null) {
						System.err.println("File does not end in End: "+f);
						System.exit(4);
					}
					int idx = line.indexOf('=');
					if(idx == -1) {
						// Last line
						if(!line.equals("End")) {
							System.err.println("Line with no equals (file does not end in End???): "+f+" - \""+line+"\"");
							System.exit(1);
						}
						sw.append(line+"\n");
						line = br.readLine();
						if(line != null) {
							System.err.println("Content after End: \""+line+"\"");
							System.exit(2);
						}
						break;
					}
					String before = line.substring(0, idx);
					//String after = line.substring(idx+1);
					String s = english.get(before);
					if(s == null) {
						System.err.println("Orphaned string: \""+before+"\" in "+f);
						changed = true;
						continue;
					}
					sw.append(line+"\n");
				}
				if(!changed) continue;
				try(OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")) {
					osw.write(sw.toString());
				}
				System.out.println("Rewritten "+f);
			}
		}
	}

}
