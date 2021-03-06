package de.janrufmonitor.application;

import java.io.*;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;

import de.janrufmonitor.util.io.PathResolver;
import de.janrufmonitor.classloader.JamCacheMasterClassLoader;
import de.janrufmonitor.exception.Message;
import de.janrufmonitor.exception.PropagationFactory;
import de.janrufmonitor.framework.IJAMConst;
import de.janrufmonitor.framework.command.ICommand;
import de.janrufmonitor.logging.LoggingInitializer;
import de.janrufmonitor.runtime.PIMRuntime;

public class RunUI {
    
    private static Logger m_logger;
       
    private static class PIMCommandLine {
    	   	
    	public static String[] getStartupCommands(String[] args) {
    		String commandArg = null;
    		for (int i=0;i<args.length;i++) {
    			if (args[i] != null && args[i].toLowerCase().startsWith("-c:")){
					commandArg = args[i].substring(3);
    			}
    		}
    		
    		if (commandArg!=null) {
    			StringTokenizer st = new StringTokenizer(commandArg, ",");
    			
				String[] sCommands = new String[st.countTokens()];
				int tc = 0;
				while (st.hasMoreTokens()) {
					sCommands[tc] = st.nextToken().trim();
					tc++;
				}
				return sCommands;
    		}
    		return new String[0];
    	}    	
    }
    
    public static void go() {
		try {
			LoggingInitializer.run();

			RunUI.m_logger = LogManager.getLogManager().getLogger(IJAMConst.DEFAULT_LOGGER);
			RunUI.m_logger.info("Starting ...");
			RunUI.m_logger.info("Running version " + IJAMConst.VERSION_DISPLAY + " (Build: "+IJAMConst.VERSION_BUILD+")");

			if (RunUI.m_logger.isLoggable(Level.FINE)){
				Properties env = System.getProperties();
				Iterator iter = env.keySet().iterator();
				String key = null;
				RunUI.m_logger.fine("Reading Java runtime environment...");
				while (iter.hasNext()) {
					key = (String) iter.next();
					RunUI.m_logger.fine(key + " = "+env.getProperty(key));
				}
			}

			// set Jam classloader
			if (JamCacheMasterClassLoader.getInstance().isValid()) {
				Thread.currentThread().setContextClassLoader(JamCacheMasterClassLoader.getInstance());
				RunUI.m_logger.info("Set new context classloader...");
				PIMRuntime.getInstance().startup();
			
				RunUI.registerShutdownHook();
				
			} else {
				Thread thread = new Thread () {
					public void run () {
						Display.getDefault().syncExec(
							new Runnable () {
								public void run () {
									Shell shell = new Shell(Display.getDefault());
									shell.setSize(0,0);
									int style = SWT.APPLICATION_MODAL | SWT.OK;
									MessageBox messageBox = new MessageBox (shell, style);
									String lang = System.getProperty("user.language");
									if (lang==null) lang = "de";									
									messageBox.setMessage (lang.equalsIgnoreCase("de") ? "jAnrufmonitor kann nicht erneut gestartet werden. Er ist\nbereits gestartet und kann nur einmal ausgef\u00FChrt werden." : "jAnrufmonitor already running.");
									messageBox.setText(lang.equalsIgnoreCase("de") ? "jAnrufmonitor - Fehler beim Programmstart": "jAnrufmonitor Error...");
									if (messageBox.open () == SWT.OK) {
										RunUI.m_logger.severe("Emergency exit: jAnrufmonitor already running! Failed to initialize JamCacheMasterClassLoader. Aborting startup. Make sure that only one instance of jAnrufmonitor is running and clear all files from folder: %jam-installpath%/lib/cache.");							
										System.exit(0);
									}
								}
							}
						);
					}
				};
				thread.setName("JAM-ShutdownNotifier-Thread-(non-deamon)");
				thread.start();

				try {
					Thread.sleep(30000); 
				} catch (InterruptedException e) {
					RunUI.m_logger.log(Level.SEVERE, e.getMessage(), e);
				}
		
				RunUI.m_logger.severe("Emergency exit: jAnrufmonitor already running! Failed to initialize JamCacheMasterClassLoader. Aborting startup. Make sure that only one instance of jAnrufmonitor is running and clear all files from folder: %jam-installpath%/lib/cache.");
				System.exit(0); 
			}

		} catch (Exception ex) {
			RunUI.m_logger.severe("Emergency exit: Unexpected error: " + ex.toString() +": "+ex.getMessage()+" - "+ex.getCause());
			RunUI.m_logger.severe("Program terminated unexpected. Please check your Java installation and jAnrufmonitor configuration for problems.");

			RunUI.dumpException(ex);
			PropagationFactory.getInstance().fire(new Message(ex));
			
			System.out.println("Emergency exit: Unexpected error: " + ex.toString() +": "+ex.getMessage()+" - "+ex.getCause());
			System.exit(0);
		} 
    }
    
    public static void quit() {
		RunUI.m_logger.info("Stopping ...");
		PIMRuntime.getInstance().shutdown();
		//System.exit(0);
    }
    
    private static void dumpException(Exception ex){
    	try {
	    	String dumpPath = PathResolver.getInstance(PIMRuntime.getInstance()).getLogDirectory()+"_fatal_error.dmp";
	    	FileOutputStream os = new FileOutputStream(dumpPath);
	    	PrintStream ps = new PrintStream(os);
	    	ex.printStackTrace(ps);
	    	ps.flush();
	    	ps.close();
    	} catch (FileNotFoundException e) {
    		RunUI.m_logger.severe("--> DUMPING ERROR: " + e.toString() +": "+e.getMessage()+" - "+e.getCause());
		}
    }
    
    private static void registerShutdownHook() {
		// register shutdown Hook		
		String value = PIMRuntime.getInstance().getConfigManagerFactory().getConfigManager().getProperty(IJAMConst.GLOBAL_NAMESPACE, "shutdownhook");
		if (value!=null && value.length()>0) {
			if (value.equalsIgnoreCase("false")) {
				RunUI.m_logger.info("ShutdownHook registration ignored.");
				return;
			}	
		}
		Runtime.getRuntime().addShutdownHook(new ShutDownHook());
		RunUI.m_logger.info("ShutdownHook successfully registered.");
    }

	private static void processStartupCommand(String[] commands) {
		if (commands.length==0) return;

		for (int i=0;i<commands.length;i++) {
			RunUI.startCommandThread(commands[i]);
		}
	}
	
	private static void startCommandThread(final String command) {
		Thread t = new Thread(
			new Runnable() {
				public void run() {
					ICommand c = PIMRuntime.getInstance().getCommandFactory().getCommand(command);
					if (c!=null && c.isExecutable()) {
						RunUI.m_logger.info("Start executing command <"+c.getID()+">.");
						if (!c.isExecuting()) {
							try {
								c.execute();
							} catch (Exception e) {
								m_logger.severe(e.getMessage());
							}
						} else {
							RunUI.m_logger.info("Command <"+c.getID()+"> already executing.");
						}
					} else {
						RunUI.m_logger.warning("Command does not exist.");
					}
				}
			}
		);
		t.setName("JAM-StartupExecuter#"+command+"-Thread-(non-deamon)");
		t.start();
	}

    public static void main(String[] args) { 
    	final Runnable error = new Runnable () {
			public void run () {
				Shell shell = new Shell(Display.getDefault());
				shell.setSize(0,0);
				int style = SWT.APPLICATION_MODAL | SWT.OK;
				MessageBox messageBox = new MessageBox (shell, style);
				String lang = System.getProperty("user.language");
				if (lang==null) lang = "de";									
				messageBox.setMessage (lang.equalsIgnoreCase("de") ? "jAnrufmonitor muss neu gestartet werden, da eine Aktualisierung durchgeführt wurde." : "jAnrufmonitor needs to be restarted due to an update of the core component.");
				messageBox.setText(lang.equalsIgnoreCase("de") ? "jAnrufmonitor - Aktualisierung erfolgreich": "jAnrufmonitor - Update successfully installed");
				if (messageBox.open () == SWT.OK) {
					RunUI.m_logger.warning("Emergency restart: jAnrufmonitor needs to be restarted due to changes of the core component.");
		    		PIMRuntime.getInstance().shutdown();
					JamCacheMasterClassLoader.invalidateInstance();
					System.exit(0);
				}
			}
		};
		
    	Thread thread = new Thread () {
			public void run () {
				Display.getDefault().syncExec(
					error
				);
			}
		};
		thread.setName("JAM-EmergencyShutdown-Thread-(non-deamon)");
		
    	try {
    		RunUI.go();
    		RunUI.processStartupCommand(PIMCommandLine.getStartupCommands(args));
	    	while (true) {
	    		try {
					Thread.sleep(Short.MAX_VALUE);
				} catch (InterruptedException e) {
					LogManager.getLogManager().getLogger(IJAMConst.DEFAULT_LOGGER).severe(e.getMessage());
				}
	    	}
    	} catch (Error e) {    
    		String isInstallerRun = System.getProperty(IJAMConst.SYSTEM_INSTALLER_RUN);
    		if (isInstallerRun!=null && isInstallerRun.equalsIgnoreCase("true")) {
    			thread.start();			
    		} else {
    			System.out.println("Emergency exit: Unexpected error: " + e.toString() +": "+e.getMessage()+" - "+e.getCause());
    			System.exit(0);
    		}
		}
    }   
}
