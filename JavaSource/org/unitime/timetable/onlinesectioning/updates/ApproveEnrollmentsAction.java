/*
 * UniTime 3.3 - 3.5 (University Timetabling Application)
 * Copyright (C) 2011 - 2013, UniTime LLC, and individual contributors
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
package org.unitime.timetable.onlinesectioning.updates;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.unitime.localization.impl.Localization;
import org.unitime.timetable.gwt.resources.StudentSectioningMessages;
import org.unitime.timetable.gwt.shared.SectioningException;
import org.unitime.timetable.model.StudentClassEnrollment;
import org.unitime.timetable.onlinesectioning.OnlineSectioningAction;
import org.unitime.timetable.onlinesectioning.OnlineSectioningHelper;
import org.unitime.timetable.onlinesectioning.OnlineSectioningLog;
import org.unitime.timetable.onlinesectioning.OnlineSectioningServer;
import org.unitime.timetable.onlinesectioning.OnlineSectioningServer.Lock;
import org.unitime.timetable.onlinesectioning.model.XApproval;
import org.unitime.timetable.onlinesectioning.model.XCourseRequest;
import org.unitime.timetable.onlinesectioning.model.XEnrollment;
import org.unitime.timetable.onlinesectioning.model.XEnrollments;
import org.unitime.timetable.onlinesectioning.model.XOffering;
import org.unitime.timetable.onlinesectioning.model.XSection;
import org.unitime.timetable.onlinesectioning.model.XStudent;
import org.unitime.timetable.onlinesectioning.server.CheckMaster;
import org.unitime.timetable.onlinesectioning.server.CheckMaster.Master;

/**
 * @author Tomas Muller
 */
@CheckMaster(Master.REQUIRED)
public class ApproveEnrollmentsAction implements OnlineSectioningAction<Boolean> {
	private static final long serialVersionUID = 1L;
	private static StudentSectioningMessages MSG = Localization.create(StudentSectioningMessages.class);
	private Long iOfferingId;
	private Collection<Long> iStudentIds;
	private Collection<Long> iCourseIdsCanApprove;
	private String iApproval;
	
	public ApproveEnrollmentsAction withParams(Long offeringId, Collection<Long> studentIds, Collection<Long> courseIdsCanApprove, String approval) {
		iOfferingId = offeringId;
		iStudentIds = studentIds;
		iCourseIdsCanApprove = courseIdsCanApprove;
		iApproval = approval;
		return this;
	}
	
	public Long getOfferingId() { return iOfferingId; }
	public Collection<Long> getStudentIds() { return iStudentIds; }
	public String getApproval() { return iApproval; }

	@Override
	public Boolean execute(OnlineSectioningServer server, OnlineSectioningHelper helper) {
		Lock lock = server.lockOffering(getOfferingId(), getStudentIds(), false);
		try {
			helper.getAction().addOther(OnlineSectioningLog.Entity.newBuilder()
					.setUniqueId(getOfferingId())
					.setType(OnlineSectioningLog.Entity.EntityType.OFFERING));

			String[] approval = getApproval().split(":");
			Date approvedDate = new Date(Long.valueOf(approval[0]));

			helper.beginTransaction();
			try {
				
				XOffering offering = server.getOffering(getOfferingId());
				XEnrollments enrollments = server.getEnrollments(getOfferingId());
				if (offering == null || enrollments == null) 
					throw new SectioningException(MSG.exceptionBadOffering());
				
				for (XCourseRequest request: enrollments.getRequests()) {
					XEnrollment enrollment = request.getEnrollment();
					if (enrollment == null || !enrollment.getOfferingId().equals(getOfferingId())) continue;
						if (getStudentIds().contains(enrollment.getStudentId()) && iCourseIdsCanApprove.contains(enrollment.getCourseId())) {
							
							XStudent student = server.getStudent(enrollment.getStudentId());
							if (student == null) continue;
							
							
							OnlineSectioningLog.Action.Builder action = helper.addAction(this, server.getAcademicSession());
							action.setStudent(
									OnlineSectioningLog.Entity.newBuilder()
									.setUniqueId(student.getStudentId())
									.setExternalId(student.getExternalId())
									.setName(student.getName()));
							action.addRequest(OnlineSectioningHelper.toProto(request));
							OnlineSectioningLog.Enrollment.Builder enrl = OnlineSectioningLog.Enrollment.newBuilder();
							enrl.setType(OnlineSectioningLog.Enrollment.EnrollmentType.APPROVED);
							for (XSection assignment: offering.getSections(enrollment))
								enrl.addSection(OnlineSectioningHelper.toProto(assignment, enrollment));
							action.addOther(OnlineSectioningLog.Entity.newBuilder()
									.setUniqueId(offering.getOfferingId())
									.setName(offering.getName())
									.setType(OnlineSectioningLog.Entity.EntityType.OFFERING));
							action.addOther(OnlineSectioningLog.Entity.newBuilder()
									.setName(approval[2])
									.setExternalId(approval[1])
									.setType(OnlineSectioningLog.Entity.EntityType.MANAGER));
							action.addEnrollment(enrl);
							
							XEnrollment oldEnrollment = new XEnrollment(enrollment);
							
							enrollment.setApproval(new XApproval(approval[1], approvedDate, approval[2]));
							XCourseRequest r = server.assign(request, enrollment);
							if (r != null && r.getEnrollment() != null) {
								for (StudentClassEnrollment e: (List<StudentClassEnrollment>)helper.getHibSession().createQuery(
										"from StudentClassEnrollment e where e.student.uniqueId = :studentId and e.courseOffering.instructionalOffering = :offeringId")
										.setLong("studentId", enrollment.getStudentId())
										.setLong("offeringId", getOfferingId())
										.list()) {
									e.setApprovedBy(approval[1]);
									e.setApprovedDate(approvedDate);
									helper.getHibSession().saveOrUpdate(e);
								}
								
								server.execute(server.createAction(NotifyStudentAction.class).forStudent(enrollment.getStudentId()).oldEnrollment(offering, oldEnrollment), helper.getUser());
							}
						}
					}

				helper.commitTransaction();
				return true;			
			} catch (Exception e) {
				helper.rollbackTransaction();
				if (e instanceof SectioningException)
					throw (SectioningException)e;
				throw new SectioningException(MSG.exceptionUnknown(e.getMessage()), e);
			}
		} finally {
			lock.release();
		}
	}

	@Override
	public String name() { return "approve-enrollments"; }

}
