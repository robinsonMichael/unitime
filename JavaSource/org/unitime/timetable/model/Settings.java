/*
 * UniTime 3.2 (University Timetabling Application)
 * Copyright (C) 2008 - 2010, UniTime LLC, and individual contributors
 * as indicated by the @authors tag.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
*/
package org.unitime.timetable.model;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.hibernate.criterion.Restrictions;
import org.unitime.commons.Debug;
import org.unitime.commons.User;
import org.unitime.timetable.model.base.BaseSettings;
import org.unitime.timetable.model.dao.SettingsDAO;
import org.unitime.timetable.model.dao._RootDAO;
import org.unitime.timetable.security.UserContext;




public class Settings extends BaseSettings {
	private static final long serialVersionUID = 1L;

/*[CONSTRUCTOR MARKER BEGIN]*/
	public Settings () {
		super();
	}

	/**
	 * Constructor for primary key
	 */
	public Settings (java.lang.Long uniqueId) {
		super(uniqueId);
	}

/*[CONSTRUCTOR MARKER END]*/

    /**
     * Returns all the settings for a user
     * @param request request object 
     * @param user User object
     * @return Set containing settings, null otherwise
     */
	
    public static Set getSettings(User user) {
        TimetableManager tm = TimetableManager.getManager(user);
        
	    return (tm==null?null:tm.getSettings());
    }
    
    public static Set getSettings(UserContext user) {
        TimetableManager tm = TimetableManager.findByExternalId(user.getExternalUserId());
        
	    return (tm==null?null:tm.getSettings());
    }
    
	
    /**
     * Retrieves the user setting id /value if exists
     * @param currRole Current Role
     * @param uSettings User Settings Object
     * @param keyId Setting UniqueId
     * @param defaultValue Default Value
     * @return Array of Setting id /value if found, otherwise returns -1 / default value
     */
    public static String[] getSettingValue(String currRole, Set uSettings, Long keyId, String defaultValue) {
        String[] data = new String[2];
        data[0] = "-1";
        data[1] = defaultValue;
        if (uSettings==null) return data;

        org.hibernate.Session hibSession = null;
        
		try {
		    _RootDAO rootDao = new _RootDAO();
			hibSession = rootDao.getSession();
			Iterator i = uSettings.iterator();

	        while (i.hasNext()) {
	            ManagerSettings mgrSettings = (ManagerSettings) i.next();
				hibSession.update(mgrSettings);
				
	            if(mgrSettings.getKey().getUniqueId().intValue()==keyId.intValue()) {
	                data[0] = mgrSettings.getUniqueId().toString();
	                data[1] = mgrSettings.getValue();
	                break;
	            }
	        }
		}
		catch (Exception ex) {
		    Debug.error(ex);
		}
		finally {
			//if (hibSession!=null && hibSession.isOpen()) hibSession.close();
		}
		
        return data;
    }

    /**
	 * Get the default value for a given key
	 * @param key Setting key
	 * @return Default value if found, null otherwise
	 */
	public static Settings getSetting(String key) {
	    Settings settings = null;
		org.hibernate.Session hibSession = null;
	    
        try {
            SettingsDAO sDao = new SettingsDAO();
			hibSession = sDao.getSession();
            
			List settingsList = hibSession.createCriteria(Settings.class)
			.add(Restrictions.eq("key", key))
			.setCacheable(true)
			.list();
			
			if(settingsList.size()!=0) 
			    settings = (Settings) settingsList.get(0);
			    
	    }
	    catch (Exception e) {
			Debug.error(e);
			settings = null;
	    }
	    finally {
	    	//if (hibSession!=null && hibSession.isOpen()) hibSession.close();
	    }
	    
	    return settings;
	}
	
	
	/**
	 * Get the user setting value for the given key
	 * @param user User object
	 * @param key Key 
	 * @return user setting or default value if none exists
	 */
	@Deprecated
	public static String getSettingValue(User user, String key) {
		Settings setting = Settings.getSetting(key);
		if (setting==null) return null;
		String defaultValue = setting.getDefaultValue();		
		Set managerSettings = Settings.getSettings(user);
		String valueArr[] = Settings.getSettingValue(user.getCurrentRole(), managerSettings, setting.getUniqueId(), defaultValue);
		return valueArr[1];
	}
	
	@Deprecated
	public static String getSettingValue(UserContext user, String key) {
		Settings setting = Settings.getSetting(key);
		if (setting==null) return null;
		String defaultValue = setting.getDefaultValue();		
		Set managerSettings = Settings.getSettings(user);
		String valueArr[] = Settings.getSettingValue(user.getCurrentRole(), managerSettings, setting.getUniqueId(), defaultValue);
		return valueArr[1];
	}
}
