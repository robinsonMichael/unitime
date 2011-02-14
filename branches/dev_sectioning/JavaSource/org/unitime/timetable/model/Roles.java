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

import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

import org.hibernate.criterion.Order;
import org.unitime.timetable.model.base.BaseRoles;
import org.unitime.timetable.model.dao.RolesDAO;




public class Roles extends BaseRoles {

/**
	 *
	 */
	private static final long serialVersionUID = 3256722879445154100L;

/*[CONSTRUCTOR MARKER BEGIN]*/
	public Roles () {
		super();
	}

	/**
	 * Constructor for primary key
	 */
	public Roles (java.lang.Long roleId) {
		super(roleId);
	}

/*[CONSTRUCTOR MARKER END]*/

	public static String ADMIN_ROLE = "Administrator";
	public static String DEPT_SCHED_MGR_ROLE = "Dept Sched Mgr";
	public static String VIEW_ALL_ROLE = "View All";
	public static String EXAM_MGR_ROLE = "Exam Mgr";
	public static String EVENT_MGR_ROLE = "Event Mgr";
	public static String CURRICULUM_MGR_ROLE = "Curriculum Mgr";
	
    public static String USER_ROLES_ATTR_NAME = "userRoles";
    public static String ROLES_ATTR_NAME = "rolesList";

	/**
	 * Define Admin and non - admin roles
	 */
	
    private static String[] adminRoles = new String[] { 
    	Roles.ADMIN_ROLE };
    
    private static String[] nonAdminRoles = new String[] { 
    	Roles.DEPT_SCHED_MGR_ROLE, 
    	Roles.VIEW_ALL_ROLE, 
    	Roles.EXAM_MGR_ROLE,
    	Roles.EVENT_MGR_ROLE };
    
    /**
     * Retrieve admin roles
     * @return String Array of admin roles (defined in Roles class) 
     * @see Roles
     */
	
	public static String[] getAdminRoles() {
	    return adminRoles;
	}
	
    /**
     * Retrieve non-admin roles
     * @return String Array of admin roles (defined in Roles class) 
     * @see Roles
     */
	
	public static String[] getNonAdminRoles() {
	    return nonAdminRoles;
	}

    /** Roles List **/
    private static Vector rolesList = null;
    
	/**
	 * Retrieves all roles in the database
	 * ordered by column label
	 * @param refresh true - refreshes the list from database
	 * @return Vector of Roles objects
	 */
    public static synchronized Vector getRolesList(boolean refresh) {
        
        if(rolesList!=null && !refresh)
            return rolesList;
        

        RolesDAO rdao = new RolesDAO();

        List l = rdao.findAll(Order.asc("abbv"));
        rolesList = new Vector(l);
        return rolesList;
    }
    
    /**
     * Get icon file name corresponding to role
     * @param roleRef
     * @return icon file name
     */
    public static String getRoleIcon(String roleRef) {
        if (roleRef.equals(Roles.ADMIN_ROLE))
            return "admin-icon.gif";
        if (roleRef.equals(Roles.DEPT_SCHED_MGR_ROLE))
            return "dept-mgr-icon.gif";
        if (roleRef.equals(Roles.VIEW_ALL_ROLE))
            return "view-all-icon.gif";
        
        return "other-role-icon.gif";
                
    }
    
    public static Roles getRole(String roleRef) {
        for (Enumeration e=getRolesList(false).elements();e.hasMoreElements();) {
            Roles role = (Roles)e.nextElement();
            if (roleRef.equals(role.getReference())) return role;
        }
        return null;
    }
}
