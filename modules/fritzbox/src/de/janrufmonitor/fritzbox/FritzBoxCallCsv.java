package de.janrufmonitor.fritzbox;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.janrufmonitor.framework.IAttributeMap;
import de.janrufmonitor.framework.ICall;
import de.janrufmonitor.framework.ICaller;
import de.janrufmonitor.framework.ICip;
import de.janrufmonitor.framework.IMsn;
import de.janrufmonitor.framework.IJAMConst;
import de.janrufmonitor.framework.IPhonenumber;
import de.janrufmonitor.framework.monitor.PhonenumberAnalyzer;
import de.janrufmonitor.framework.monitor.PhonenumberInfo;
import de.janrufmonitor.repository.identify.Identifier;
import de.janrufmonitor.runtime.IRuntime;
import de.janrufmonitor.runtime.PIMRuntime;

public class FritzBoxCallCsv extends AbstractFritzBoxCall {

	public FritzBoxCallCsv(String csvline, Properties config) {
		// Typ;Datum;Name;Rufnummer;Nebenstelle;Eigene Rufnummer;Dauer 
		// 1;18.08.06 10:07;;02736294863;FON S0;911955;0:01
		super(csvline, config);
	}
	
	public Date getPrecalculatedDate() {
		if (this.m_line==null || this.m_line.trim().length()==0) return null;
		
		/**
		 * Added 2011/01/05: added do to NumberFormatException in log, remove the header
		 */
		if (this.m_line.trim().toLowerCase().startsWith("typ;")) return null;
		try {
			String call[] = this.m_line.split(";");
			if (call.length>=7) {
				// create call data
				SimpleDateFormat sdf = new SimpleDateFormat(this.m_config.getProperty(CFG_DATEFORMAT, "dd.MM.yy HH:mm"));
				try {
					return sdf.parse(call[1]);
				} catch (ParseException e) {
					Logger.getLogger(IJAMConst.DEFAULT_LOGGER).severe("Wrong date format detected.");
				}
			}
		} catch (NumberFormatException ex) {
			Logger.getLogger(IJAMConst.DEFAULT_LOGGER).log(Level.SEVERE, ex.toString(), ex);
		} catch (Exception ex) {
			Logger.getLogger(IJAMConst.DEFAULT_LOGGER).warning(ex.toString() + ":" +ex.getMessage() + " : problem with line parsing : "+this.m_line);
			if (ex instanceof NullPointerException)
				Logger.getLogger(IJAMConst.DEFAULT_LOGGER).log(Level.SEVERE, ex.toString(), ex);
		}
		return null;
	}

	public ICall toCall() {
		if (this.m_line==null || this.m_line.trim().length()==0) return null;
		
		/**
		 * Added 2011/01/05: added do to NumberFormatException in log, remove the header
		 */
		if (this.m_line.trim().toLowerCase().startsWith("typ;")) return null;

		if (this.m_call==null) {
			try {
				IRuntime r = PIMRuntime.getInstance();
				String call[] = this.m_line.split(";");
				if (call.length>=7) {
					// create msn
					IMsn msn = r.getCallFactory().createMsn(getFestnetzAlias(call[5]), "");
					
					// create caller data
					int state = Integer.parseInt(call[0]);
					String callByCall = null;
					ICaller caller = null;

					
					IPhonenumber pn = PhonenumberAnalyzer.getInstance().createClirPhonenumberFromRaw(call[3].trim());
					// if no CLIR call, check internal
					if (pn==null && state !=3) pn = PhonenumberAnalyzer.getInstance().createInternalPhonenumberFromRaw(call[3].trim(), msn.getMSN());
					// if no internal call, check regular
					if (pn==null && state !=3)  {
						// if incoming call does not start with 0, the Provider number seems to have the wrong format
						// assume it is an international format 4971657110
						if (!call[3].startsWith("0") && !PhonenumberInfo.containsSpecialChars(call[3])) {
							call[3] = "00"+call[3];
						}
						pn = PhonenumberAnalyzer.getInstance().createPhonenumberFromRaw(call[3].trim(), msn.getMSN());
					}
					if (pn==null && state == 3)  {
						// added 2006/08/10: trim call-by-call information
						// only can occure on state 3 (out-going calls)
						callByCall = getCallByCall(call[3]);
						if (callByCall!=null) {
							call[3] = call[3].substring(callByCall.length());
						}
						pn = PhonenumberAnalyzer.getInstance().createInternalPhonenumberFromRaw(call[3].trim(), msn.getMSN());
						if (pn==null) {
							// added 2011/01/13: added areacode additional on special FritzBox mode. Having no leading 0, 
							// requires addition of areacode
							if (!call[3].startsWith("0")) {
								Logger.getLogger(IJAMConst.DEFAULT_LOGGER).info("Assuming number "+call[3]+" has missing areacode.");
								call[3] = this.getGeneralAreaCode() + call[3];
								Logger.getLogger(IJAMConst.DEFAULT_LOGGER).info("Added areacode to number "+call[3]);
							}
							pn = PhonenumberAnalyzer.getInstance().createPhonenumberFromRaw(call[3].trim(), msn.getMSN());
						}
					}
					caller = Identifier.identify(r, pn);
					
					/**
					IPhonenumber pn = r.getCallerFactory().createPhonenumber(true);
					if (!PhonenumberInfo.isClired(call[3].trim())) {
						if (state != 3 && (PhonenumberInfo.isInternalNumber(call[3].trim()) || PhonenumberInfo.containsSpecialChars(call[3].trim()))) {
							pn = r.getCallerFactory().createInternalPhonenumber(call[3].trim());
						} else if (state==3){
							// added 2006/08/10: trim call-by-call information
							// only can occure on state 3 (out-going calls)
							callByCall = getCallByCall(call[3]);
							if (callByCall!=null) {
								call[3] = call[3].substring(callByCall.length());
							}
							call[3] = call[3].trim().substring(PhonenumberInfo.getTruncateNumber(msn), call[3].trim().length());
							
							if (PhonenumberInfo.isInternalNumber(call[3].trim()) || PhonenumberInfo.containsSpecialChars(call[3].trim())){
								// added 2010/03/03 if outgoing call is an internal call (rare, but could occure)
								pn = r.getCallerFactory().createInternalPhonenumber(call[3].trim());
							} else
								pn =  r.getCallerFactory().createPhonenumber((call[3].startsWith("0") ? call[3].substring(1) : (getGeneralAreaCode() + call[3]).substring(1)));
						} else {
							if (!call[3].startsWith("0")) {
								call[3] = "00"+call[3];
							}
							call[3] = call[3].trim().substring(PhonenumberInfo.getTruncateNumber(msn), call[3].trim().length());

							pn =  r.getCallerFactory().createPhonenumber((call[3].startsWith("0") ? call[3].substring(1) : (getGeneralAreaCode() + call[3]).substring(1)));
						}
						caller = Identifier.identify(r, pn);
					}
					*/
					
					if (caller==null) {
						caller = r.getCallerFactory().createCaller(pn);
					}
					
					// create call data
					SimpleDateFormat sdf = new SimpleDateFormat(this.m_config.getProperty(CFG_DATEFORMAT, "dd.MM.yy HH:mm"));
					Date date = new Date(0);
					try {
						date = sdf.parse(call[1]);
					} catch (ParseException e) {
						Logger.getLogger(IJAMConst.DEFAULT_LOGGER).severe("Wrong date format detected.");
					}
									
					//IMsn msn = r.getCallFactory().createMsn(getFestnetzAlias(call[5]), "");
					ICip cip = r.getCallFactory().createCip(getCip(call[4]), "");
	
					if (call[5].startsWith("Internet:")) {
						msn.setMSN(call[5].substring("Internet:".length()).trim());
						cip.setCIP("100"); // VOIP CIP
					} 
					msn.setAdditional(r.getMsnManager().getMsnLabel(msn));
					cip.setAdditional(r.getCipManager().getCipLabel(cip, ""));
					
					// create attributes
					IAttributeMap am = r.getCallFactory().createAttributeMap();
					switch (state) {
						case 1: am.add(r.getCallFactory().createAttribute(IJAMConst.ATTRIBUTE_NAME_CALLSTATUS, IJAMConst.ATTRIBUTE_VALUE_ACCEPTED)); break;
						case 3: am.add(r.getCallFactory().createAttribute(IJAMConst.ATTRIBUTE_NAME_CALLSTATUS, IJAMConst.ATTRIBUTE_VALUE_OUTGOING)); break;
						default: am.add(r.getCallFactory().createAttribute(IJAMConst.ATTRIBUTE_NAME_CALLSTATUS, IJAMConst.ATTRIBUTE_VALUE_MISSED)); break;
					}
					am.add(r.getCallFactory().createAttribute("fritzbox.line", call[4]));
					am.add(r.getCallFactory().createAttribute("fritzbox.duration", Integer.toString(getTime(call[6]))));
					if (callByCall!=null)
						am.add(r.getCallFactory().createAttribute("fritzbox.callbycall", callByCall));
					
					// create UUID
					StringBuffer uuid = new StringBuffer();
					uuid.append(date.getTime());
					uuid.append("-");
					uuid.append(pn.getTelephoneNumber());
					uuid.append("-");
					uuid.append(msn.getMSN());
					
					// limit uuid to 32 chars
					if (uuid.length()>31) {
						// reduce byte length to append -1 for redundant calls max -1-1 --> 3 calls
						uuid = new StringBuffer(uuid.substring(0,31));
					}
					//uuid = new StringBuffer(FritzBoxUUIDManager.getInstance().calculateUUID(uuid.toString()));
					
					this.m_call = r.getCallFactory().createCall(uuid.toString(), caller, msn, cip, date);
					this.m_call.setAttributes(am);
				}
			} catch (NumberFormatException ex) {
				Logger.getLogger(IJAMConst.DEFAULT_LOGGER).log(Level.SEVERE, ex.toString(), ex);
			} catch (Exception ex) {
				Logger.getLogger(IJAMConst.DEFAULT_LOGGER).warning(ex.toString() + ":" +ex.getMessage() + " : problem with line parsing : "+this.m_line);
				if (ex instanceof NullPointerException)
					Logger.getLogger(IJAMConst.DEFAULT_LOGGER).log(Level.SEVERE, ex.toString(), ex);
				return null;
			}
		}
		return this.m_call;
	}

	private int getTime(String t) {
		String[] time = t.split(":");
		if (time.length==2) {
			int r = Integer.parseInt(time[0]) * 60 * 60;
			r += (Integer.parseInt(time[1]) * 60);
			return r;
		}
		return 0;
	}
}
