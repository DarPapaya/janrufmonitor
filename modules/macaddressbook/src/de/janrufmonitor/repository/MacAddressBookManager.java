package de.janrufmonitor.repository;

import java.util.logging.Level;

import de.janrufmonitor.framework.IAttribute;
import de.janrufmonitor.framework.IAttributeMap;
import de.janrufmonitor.framework.ICaller;
import de.janrufmonitor.framework.ICallerList;
import de.janrufmonitor.framework.IJAMConst;
import de.janrufmonitor.framework.IPhonenumber;
import de.janrufmonitor.macab.MacAddressBookProxy;
import de.janrufmonitor.repository.AbstractReadWriteCallerManager;
import de.janrufmonitor.repository.filter.AttributeFilter;
import de.janrufmonitor.repository.filter.FilterType;
import de.janrufmonitor.repository.filter.IFilter;
import de.janrufmonitor.repository.filter.PhonenumberFilter;
import de.janrufmonitor.repository.imexport.ITracker;
import de.janrufmonitor.repository.types.IIdentifyCallerRepository;
import de.janrufmonitor.repository.types.IReadCallerRepository;
import de.janrufmonitor.repository.types.IRemoteRepository;
import de.janrufmonitor.runtime.IRuntime;
import de.janrufmonitor.runtime.PIMRuntime;

public class MacAddressBookManager extends AbstractReadWriteCallerManager implements IRemoteRepository, ITracker {

	private static String ID = "MacAddressBookManager";
	public static String NAMESPACE = "repository.MacAddressBookManager";
	
	private IRuntime m_runtime;
	private MacAddressBookProxy m_proxy;

	public MacAddressBookManager() {
		super();
		this.getRuntime().getConfigurableNotifier().register(this);
	}
	
	public String getID() {
		return MacAddressBookManager.ID;
	}

	public String getNamespace() {
		return MacAddressBookManager.NAMESPACE;
	}

	public IRuntime getRuntime() {
		if (this.m_runtime == null)
			this.m_runtime = PIMRuntime.getInstance();
		return this.m_runtime;
	}

	public ICaller getCaller(IPhonenumber number) throws CallerNotFoundException {
		if (number == null)
			throw new CallerNotFoundException(
					"Phone number is not set (null). No caller found.");

		if (number.isClired())
			throw new CallerNotFoundException(
					"Phone number is CLIR. Identification impossible.");

		try {
			ICaller c = getProxy().findContact(number);
			if (c!=null) return c;
		} catch (MacAddressBookProxyException e) {
			this.m_logger.log(Level.SEVERE, e.getMessage(), e);
		}
		
		throw new CallerNotFoundException(
				"No caller entry found for phonenumber : "
						+ number.getTelephoneNumber());
	}

	public ICallerList getCallers(IFilter filter) {
		try {
			if (filter!=null && filter.getType().equals(FilterType.ATTRIBUTE)) {
				IAttributeMap m = ((AttributeFilter)filter).getAttributeMap();
				if (m.contains(IJAMConst.ATTRIBUTE_NAME_CATEGORY)) {
					IAttribute a = m.get(IJAMConst.ATTRIBUTE_NAME_CATEGORY);
					return getProxy().getContacts(a.getValue());
				}				
			}
			if (filter!=null && filter.getType().equals(FilterType.PHONENUMBER)) {
				String intarea = ((PhonenumberFilter)filter).getPhonenumber().getIntAreaCode();
				String area = ((PhonenumberFilter)filter).getPhonenumber().getAreaCode();
				return getProxy().getContactsByAreaCode(intarea,area);
			}
			return getProxy().getContacts(null);
		} catch (MacAddressBookProxyException e) {
			this.m_logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return getRuntime().getCallerFactory().createCallerList();
	}

	public void removeCaller(ICaller caller) {
		try {
			getProxy().removeContact(caller);
		} catch (MacAddressBookProxyException e) {
			this.m_logger.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	public void setCaller(ICaller caller) {
		try {
			getProxy().createContact(caller);
		} catch (MacAddressBookProxyException e) {
			this.m_logger.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	public void updateCaller(ICaller caller) {
		try {
			if (!getProxy().updateContact(caller))
				getProxy().createContact(caller);
		} catch (MacAddressBookProxyException e) {
			this.m_logger.log(Level.SEVERE, e.getMessage(), e);
		}
	}
		
	private MacAddressBookProxy getProxy() {
		if (this.m_proxy==null) {
			this.m_proxy = MacAddressBookProxy.getInstance();
			this.m_proxy.start();
		}			
		return m_proxy;
	}

	public void shutdown() {
		if (this.m_proxy!=null) this.m_proxy.stop();
		MacAddressBookProxy.invalidate();
		this.m_proxy = null;
		super.shutdown();
		
	}

	public int getCurrent() {
		return getProxy().getCurrent();
	}

	public int getTotal() {
		return getProxy().getTotal();
	}

	public void startup() {
		super.startup();
		
		if (m_logger.isLoggable(Level.INFO))
			m_logger.info("<"+getID()+"> configuration: "+this.m_configuration);	
		
		if (this.isActive()) {
			Thread t = new Thread() {
				public void run() {
					try {
						// update database
						getProxy().preload();
					} catch (MacAddressBookProxyException e) {
						m_logger.log(Level.SEVERE, e.toString(), e);
					}
				}
			};
			t.setName("JAM-MacAddressBookStartUpSync-Thread-(deamon)");
			t.start();
		}
	}
	
	public boolean isSupported(Class c) {
		return (c.equals(IIdentifyCallerRepository.class) || c.equals(IReadCallerRepository.class) || c.equals(IRemoteRepository.class));
	}

}
