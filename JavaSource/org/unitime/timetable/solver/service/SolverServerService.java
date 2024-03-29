/*
 * UniTime 3.5 (University Timetabling Application)
 * Copyright (C) 2013, UniTime LLC, and individual contributors
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
package org.unitime.timetable.solver.service;

import java.util.List;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cpsolver.ifs.util.DataProperties;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.blocks.RpcDispatcher;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.unitime.commons.jgroups.UniTimeChannelLookup;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.onlinesectioning.OnlineSectioningServer;
import org.unitime.timetable.solver.SolverProxy;
import org.unitime.timetable.solver.exam.ExamSolverProxy;
import org.unitime.timetable.solver.jgroups.LocalSolverServer;
import org.unitime.timetable.solver.jgroups.RemoteSolverContainer;
import org.unitime.timetable.solver.jgroups.SolverContainer;
import org.unitime.timetable.solver.jgroups.SolverContainerWrapper;
import org.unitime.timetable.solver.jgroups.SolverServer;
import org.unitime.timetable.solver.jgroups.SolverServerImplementation;
import org.unitime.timetable.solver.studentsct.StudentSolverProxy;

/**
 * @author Tomas Muller
 */
@Service("solverServerService")
public class SolverServerService implements InitializingBean, DisposableBean {
	private static Log sLog = LogFactory.getLog(SolverServerService.class);
	private JChannel iChannel = null;
	private SolverServer iServer = null;
	
	private SolverContainer<SolverProxy> iCourseSolverContainer;
	private SolverContainer<ExamSolverProxy> iExamSolverContainer;
	private SolverContainer<StudentSolverProxy> iStudentSolverContainer;
	private SolverContainer<OnlineSectioningServer> iOnlineStudentSchedulingContainer;
	
	@Override
	public void afterPropertiesSet() throws Exception {
		try {
			if (ApplicationProperty.SolverClusterEnabled.isFalse()) {
				iServer = new LocalSolverServer();
				
				iServer.start();
				
				iCourseSolverContainer = iServer.getCourseSolverContainer();
				iExamSolverContainer = iServer.getExamSolverContainer();
				iStudentSolverContainer = iServer.getStudentSolverContainer();
				iOnlineStudentSchedulingContainer = iServer.getOnlineStudentSchedulingContainer();
			} else {
				iChannel = (JChannel) new UniTimeChannelLookup().getJGroupsChannel(null);
				
				iServer = new SolverServerImplementation(true, iChannel);
				
				iChannel.connect("UniTime:rpc");
				
				iServer.start();
				
				iCourseSolverContainer = new SolverContainerWrapper<SolverProxy>(
						((SolverServerImplementation)iServer).getDispatcher(),
						(RemoteSolverContainer<SolverProxy>) iServer.getCourseSolverContainer(), true);
				iExamSolverContainer = new SolverContainerWrapper<ExamSolverProxy>(
						((SolverServerImplementation)iServer).getDispatcher(),
						(RemoteSolverContainer<ExamSolverProxy>) iServer.getExamSolverContainer(), true);
				iStudentSolverContainer = new SolverContainerWrapper<StudentSolverProxy>(
						((SolverServerImplementation)iServer).getDispatcher(),
						(RemoteSolverContainer<StudentSolverProxy>) iServer.getStudentSolverContainer(), true);
				iOnlineStudentSchedulingContainer = new SolverContainerWrapper<OnlineSectioningServer>(
						((SolverServerImplementation)iServer).getDispatcher(),
						(RemoteSolverContainer<OnlineSectioningServer>) iServer.getOnlineStudentSchedulingContainer(), false);
			}
		} catch (Exception e) {
			sLog.fatal("Failed to start solver server: " + e.getMessage(), e);
		}
	}
	
	private RpcDispatcher getDispatcher() {
		if (iServer instanceof SolverServerImplementation)
			return ((SolverServerImplementation)iServer).getDispatcher();
		return null;
	}

	@Override
	public void destroy() throws Exception {
		try {
			sLog.info("Server is going down...");
			iServer.stop();
			
			if (iChannel != null) {
				sLog.info("Disconnecting from the channel...");
				iChannel.disconnect();
			
				sLog.info("Closing the channel...");
				iChannel.close();
			}
			
			iServer = null; 
		} catch (Exception e) {
			sLog.fatal("Failed to stop solver server: " + e.getMessage(), e);
		}
	}

	public List<SolverServer> getServers(boolean onlyAvailable) {
		return iServer.getServers(onlyAvailable);
	}
	
	public SolverServer getLocalServer() {
		return iServer;
	}
	
	public SolverContainer<SolverProxy> getCourseSolverContainer() {
		return iCourseSolverContainer;
	}
	
	public SolverProxy createCourseSolver(String host, String user, DataProperties properties) {
	    if (host != null) {
	    	if ("local".equals(host)) {
	    		SolverProxy solver = iServer.getCourseSolverContainer().createSolver(user, properties);
    			return solver;
	    	}	
	    	for (SolverServer server: iServer.getServers(true)) {
	    		if (server.getHost().equals(host)) {
	    			SolverProxy solver = server.getCourseSolverContainer().createSolver(user, properties);
	    			return solver;
	    		}
	    	}
	    }
	    SolverProxy solver = iCourseSolverContainer.createSolver(user, properties);
	    return solver;
	}
	
	public SolverContainer<ExamSolverProxy> getExamSolverContainer() {
		return iExamSolverContainer;
	}
	
	public ExamSolverProxy createExamSolver(String host, String user, DataProperties properties) {
	    if (host != null) {
	    	if ("local".equals(host)) {
	    		ExamSolverProxy solver = iServer.getExamSolverContainer().createSolver(user, properties);
    			return solver;
	    	}	
	    	for (SolverServer server: iServer.getServers(true)) {
	    		if (server.getHost().equals(host)) {
	    			ExamSolverProxy solver = server.getExamSolverContainer().createSolver(user, properties);
	    			return solver;
	    		}
	    	}
	    }
	    ExamSolverProxy solver = iExamSolverContainer.createSolver(user, properties);
	    return solver;
	}
	
	public SolverContainer<StudentSolverProxy> getStudentSolverContainer() {
		return iStudentSolverContainer;
	}
	
	public StudentSolverProxy createStudentSolver(String host, String user, DataProperties properties) {
	    if (host != null) {
	    	if ("local".equals(host)) {
	    		StudentSolverProxy solver = iServer.getStudentSolverContainer().createSolver(user, properties);
    			return solver;
	    	}	
	    	for (SolverServer server: iServer.getServers(true)) {
	    		if (server.getHost().equals(host)) {
	    			StudentSolverProxy solver = server.getStudentSolverContainer().createSolver(user, properties);
	    			return solver;
	    		}
	    	}
	    }
	    StudentSolverProxy solver = iStudentSolverContainer.createSolver(user, properties);
	    return solver;
	}
	
	public SolverContainer<OnlineSectioningServer> getOnlineStudentSchedulingContainer() {
		return iOnlineStudentSchedulingContainer;
	}
	
	public SolverServer getServer(String host) {
		if ("local".equals(host) || host == null)
			return iServer;
		if (iChannel != null)
			for (Address address: iChannel.getView().getMembers()) {
				if (host.equals(address.toString()))
					return iServer.crateServerProxy(address);
			}
		return null;
	}
	
	public boolean isOnlineStudentSchedulingEnabled() {
		return !getOnlineStudentSchedulingContainer().getSolvers().isEmpty();
	}
	
	public boolean isStudentRegistrationEnabled() {
        for (Session session: SessionDAO.getInstance().findAll()) {
                if (session.getStatusType().isTestSession()) continue;
                if (!session.getStatusType().canOnlineSectionStudents() && !session.getStatusType().canSectionAssistStudents() && session.getStatusType().canPreRegisterStudents()) return true;
        }
        return false;
	}
	
	public void setApplicationProperty(Long sessionId, String key, String value) {
		try {
			RpcDispatcher dispatcher = getDispatcher();
			if (dispatcher != null)
				dispatcher.callRemoteMethods(null, "setApplicationProperty", new Object[] { sessionId, key, value }, new Class[] { Long.class, String.class, String.class }, SolverServerImplementation.sAllResponses);
			else
				iServer.setApplicationProperty(sessionId, key, value);
		} catch (Exception e) {
			sLog.error("Failed to update the application property " + key + " along the cluster: " + e.getMessage(), e);
		}
	}
	
	public void setLoggingLevel(String name, Integer level) {
		try {
			RpcDispatcher dispatcher = getDispatcher();
			if (dispatcher != null)
				dispatcher.callRemoteMethods(null, "setLoggingLevel", new Object[] { name, level }, new Class[] { String.class, Integer.class }, SolverServerImplementation.sAllResponses);
			else
				iServer.setLoggingLevel(name, level);
		} catch (Exception e) {
			sLog.error("Failed to update the logging level for " + name + " along the cluster: " + e.getMessage(), e);
		}
	}
}
