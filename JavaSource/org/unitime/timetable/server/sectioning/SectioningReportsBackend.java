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
package org.unitime.timetable.server.sectioning;


import org.cpsolver.ifs.util.CSVFile;
import org.cpsolver.ifs.util.DataProperties;
import org.cpsolver.ifs.util.CSVFile.CSVLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.unitime.localization.impl.Localization;
import org.unitime.timetable.gwt.client.sectioning.SectioningReports.SectioningReportRpcRequest;
import org.unitime.timetable.gwt.client.sectioning.SectioningReports.SectioningReportRpcResponse;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.resources.GwtConstants;
import org.unitime.timetable.onlinesectioning.OnlineSectioningLog;
import org.unitime.timetable.onlinesectioning.OnlineSectioningServer;
import org.unitime.timetable.onlinesectioning.basic.GenerateSectioningReport;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.solver.service.SolverServerService;
import org.unitime.timetable.solver.service.SolverService;
import org.unitime.timetable.solver.studentsct.StudentSolverProxy;

/**
 * @author Tomas Muller
 */
@GwtRpcImplements(SectioningReportRpcRequest.class)
public class SectioningReportsBackend implements GwtRpcImplementation<SectioningReportRpcRequest, SectioningReportRpcResponse> {
	protected static GwtConstants CONSTANTS = Localization.create(GwtConstants.class);
	@Autowired SolverService<StudentSolverProxy> studentSectioningSolverService;
	@Autowired SolverServerService solverServerService;

	@Override
	public SectioningReportRpcResponse execute(SectioningReportRpcRequest request, SessionContext context) {
		DataProperties parameters = new DataProperties(request.getParameters());
		CSVFile csv =  null;
		boolean online = parameters.getPropertyBoolean("online", false);
		parameters.setProperty("useAmPm", CONSTANTS.useAmPm() ? "true" : "false");

		if (online) {
			context.checkPermission(Right.SchedulingDashboard);
			OnlineSectioningServer server = solverServerService.getOnlineStudentSchedulingContainer().getSolver(context.getUser().getCurrentAcademicSessionId().toString());
			if (server == null)
				throw new GwtRpcException("Online student scheduling is not enabled for " + context.getUser().getCurrentAuthority().getQualifiers("Session").get(0).getQualifierLabel() + ".");
			
			OnlineSectioningLog.Entity user = OnlineSectioningLog.Entity.newBuilder()
					.setExternalId(context.getUser().getExternalUserId())
					.setName(context.getUser().getName() == null ? context.getUser().getUsername() : context.getUser().getName())
					.setType(context.hasPermission(Right.StudentSchedulingAdvisor) ? OnlineSectioningLog.Entity.EntityType.MANAGER : OnlineSectioningLog.Entity.EntityType.STUDENT).build();
			
			csv = server.execute(server.createAction(GenerateSectioningReport.class).withParameters(parameters), user);
		} else {
			context.checkPermission(Right.StudentSectioningSolver);
			
			StudentSolverProxy solver = studentSectioningSolverService.getSolver();
			if (solver == null)
				throw new GwtRpcException("No student solver is running.");
			
			csv = solver.getReport(parameters);
		}
		
		if (csv == null)
			throw new GwtRpcException("No report was created.");
		
		SectioningReportRpcResponse response = new SectioningReportRpcResponse();
		String[] header = new String[csv.getHeader().getFields().size()];
		for (int i = 0; i < csv.getHeader().getFields().size(); i++)
			header[i] = csv.getHeader().getField(i).toString();
		response.addLine(header);
		if (csv.getLines() != null)
			for (CSVLine line: csv.getLines()) {
				String[] row = new String[line.getFields().size()];
				for (int i = 0; i < line.getFields().size(); i++)
					row[i] = line.getField(i).toString();
				response.addLine(row);
			}
	
		return response;
	}

}
