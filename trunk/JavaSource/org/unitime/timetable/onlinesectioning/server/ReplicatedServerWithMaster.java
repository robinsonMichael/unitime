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
package org.unitime.timetable.onlinesectioning.server;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.context.Flag;
import org.infinispan.distexec.DefaultExecutorService;
import org.infinispan.distexec.DistributedCallable;
import org.infinispan.distexec.DistributedExecutorService;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.event.CacheEntryModifiedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryRemovedEvent;
import org.unitime.timetable.gwt.shared.CourseRequestInterface;
import org.unitime.timetable.gwt.shared.SectioningException;
import org.unitime.timetable.onlinesectioning.OnlineSectioningAction;
import org.unitime.timetable.onlinesectioning.OnlineSectioningLog;
import org.unitime.timetable.onlinesectioning.OnlineSectioningServerContext;
import org.unitime.timetable.onlinesectioning.match.CourseMatcher;
import org.unitime.timetable.onlinesectioning.match.StudentMatcher;
import org.unitime.timetable.onlinesectioning.model.XCourse;
import org.unitime.timetable.onlinesectioning.model.XCourseId;
import org.unitime.timetable.onlinesectioning.model.XCourseRequest;
import org.unitime.timetable.onlinesectioning.model.XEnrollment;
import org.unitime.timetable.onlinesectioning.model.XExpectations;
import org.unitime.timetable.onlinesectioning.model.XHashSet;
import org.unitime.timetable.onlinesectioning.model.XOffering;
import org.unitime.timetable.onlinesectioning.model.XRequest;
import org.unitime.timetable.onlinesectioning.model.XStudent;
import org.unitime.timetable.onlinesectioning.model.XTreeSet;
import org.unitime.timetable.onlinesectioning.server.CheckMaster.Master;

/**
 * @author Tomas Muller
 */
public class ReplicatedServerWithMaster extends AbstractLockingServer {
	private EmbeddedCacheManager iCacheManager;
	private Map<Long, XCourseId> iCourseForId;
	private Map<String, XTreeSet<XCourseId>> iCourseForName;
	private Cache<Long, XStudent> iStudentTable;
	private Cache<Long, XOffering> iOfferingTable;
	private Map<Long, XHashSet<XCourseRequest>> iOfferingRequests;
	private Cache<Long, XExpectations> iExpectations;
	private Cache<Long, Boolean> iOfferingLocks;
	private Cache<String, Object> iConfig;

	public ReplicatedServerWithMaster(OnlineSectioningServerContext context) throws SectioningException {
		super(context);
	}
	
	private String cacheName(String table) {
		return getAcademicSession().toCompactString() + "[" + table + "]";
	}
	
	private <U,T> Cache<U,T> getCache(String name) {
		Configuration config = iCacheManager.getCacheConfiguration(name);
		if (config != null) {
			iLog.info("Using " + config + " for " + name + " cache.");
			iCacheManager.defineConfiguration(cacheName(name), config);
		}
		return iCacheManager.getCache(cacheName(name), true);
	}
	
	@Override
	protected void load(OnlineSectioningServerContext context) throws SectioningException {
		iCacheManager = context.getCacheManager();
		iCourseForId = new Hashtable<Long, XCourseId>();
		iCourseForName = new Hashtable<String, XTreeSet<XCourseId>>();
		iStudentTable = getCache("StudentTable");
		iOfferingTable = getCache("OfferingTable");
		iOfferingRequests = new HashMap<Long, XHashSet<XCourseRequest>>();
		iExpectations = getCache("Expectations");
		iOfferingLocks = getCache("OfferingLocks");
		iConfig = getCache("Config");
		
		iOfferingTable.addListener(new OfferingTableListener(iOfferingTable.values()));
		iStudentTable.addListener(new StudentTableListener(iStudentTable.values()));
		super.load(context);
	}
	
	@Override
	protected void setReady(boolean ready) {
		iConfig.put("ReadyToServe", Boolean.TRUE);
	}
	
	@Override
	public boolean isReady() {
		return Boolean.TRUE.equals(iConfig.get("ReadyToServe"));
	}

	@Override
	public void unload(boolean remove) {
		boolean master = isMaster();
		super.unload(remove);
		if (master && remove) {
			iLog.info("Removing cache.");
			iCacheManager.removeCache(cacheName("StudentTable"));
			iCacheManager.removeCache(cacheName("OfferingTable"));
			iCacheManager.removeCache(cacheName("Expectations"));
			iCacheManager.removeCache(cacheName("OfferingLocks"));
			iCacheManager.removeCache(cacheName("Config"));
		}
	}
	
	@Override
	protected void loadOnMaster(OnlineSectioningServerContext context) throws SectioningException {
		clearAll();
		releaseAllOfferingLocks();
		super.loadOnMaster(context);
	}
	
	@Override
	public Collection<XCourseId> findCourses(String query, Integer limit, CourseMatcher matcher) {
		if (matcher != null) matcher.setServer(this);
		Lock lock = readLock();
		try {
			Set<XCourseId> ret = new TreeSet<XCourseId>();
			String queryInLowerCase = query.toLowerCase();
			for (XCourseId c : iCourseForId.values()) {
				if (c.matchCourseName(queryInLowerCase) && (matcher == null || matcher.match(c))) ret.add(c);
				if (limit != null && ret.size() == limit) return ret;
			}
			if (queryInLowerCase.length() > 2) {
				for (XCourseId c : iCourseForId.values()) {
					if (c.matchTitle(queryInLowerCase) && (matcher == null || matcher.match(c))) ret.add(c);
					if (limit != null && ret.size() == limit) return ret;
				}
			}
			return ret;
		} finally {
			lock.release();
		}
	}
	
	@Override
	public Collection<XCourseId> findCourses(CourseMatcher matcher) {
		if (matcher != null) matcher.setServer(this);
		Lock lock = readLock();
		try {
			Set<XCourseId> ret = new TreeSet<XCourseId>();
			for (XCourseId c : iCourseForId.values()) {
				if (matcher.match(c)) ret.add(c);
			}
			return ret;
		} finally {
			lock.release();
		}
	}

	@Override
	public Collection<XStudent> findStudents(StudentMatcher matcher) {
		if (matcher != null) matcher.setServer(this);
		Lock lock = readLock();
		try {
			List<XStudent> ret = new ArrayList<XStudent>();
			for (XStudent s: iStudentTable.values())
				if (matcher.match(s)) ret.add(s);
			return ret;
		} finally {
			lock.release();
		}
	}

	@Override
	public XCourseId getCourse(String course) {
		Lock lock = readLock();
		try {
			if (course.indexOf('-') >= 0) {
				String courseName = course.substring(0, course.indexOf('-')).trim();
				String title = course.substring(course.indexOf('-') + 1).trim();
				TreeSet<XCourseId> infos = iCourseForName.get(courseName.toLowerCase());
				if (infos!= null && !infos.isEmpty())
					for (XCourseId info: infos)
						if (title.equalsIgnoreCase(info.getTitle())) return info;
				return null;
			} else {
				TreeSet<XCourseId> infos = iCourseForName.get(course.toLowerCase());
				if (infos!= null && !infos.isEmpty()) return infos.first();
				return null;
			}
		} finally {
			lock.release();
		}
	}
	
	private XCourse toCourse(XCourseId course) {
		if (course == null) return null;
		if (course instanceof XCourse)
			return (XCourse)course;
		XOffering offering = getOffering(course.getOfferingId());
		return offering == null ? null : offering.getCourse(course);
	}
	
	@Override
	public XCourse getCourse(Long courseId) {
		Lock lock = readLock();
		try {
			return toCourse(iCourseForId.get(courseId));
		} finally {
			lock.release();
		}
	}

	@Override
	public XStudent getStudent(Long studentId) {
		Lock lock = readLock();
		try {
			return iStudentTable.get(studentId);
		} finally {
			lock.release();
		}
	}

	@Override
	public XOffering getOffering(Long offeringId) {
		Lock lock = readLock();
		try {
			return iOfferingTable.get(offeringId);
		} finally {
			lock.release();
		}
	}

	@Override
	public Collection<XCourseRequest> getRequests(Long offeringId) {
		Lock lock = readLock();
		try {
			Collection<XCourseRequest> requests = iOfferingRequests.get(offeringId);
			return requests == null ? null : new ArrayList<XCourseRequest>(requests);
		} finally {
			lock.release();
		}		
	}

	@Override
	public XExpectations getExpectations(Long offeringId) {
		Lock lock = readLock();
		try {
			XExpectations expectations = iExpectations.get(offeringId);
			return expectations == null ? new XExpectations(offeringId) : expectations;
		} finally {
			lock.release();
		}
	}

	@Override
	public void update(XExpectations expectations) {
		if (!isMaster())
			iLog.warn("Updating expectations on a slave node. That is suspicious.");
		Lock lock = writeLock();
		try {
			iExpectations.getAdvancedCache().withFlags(Flag.IGNORE_RETURN_VALUES).put(expectations.getOfferingId(), expectations);
		} finally {
			lock.release();
		}
	}

	@Override
	public void remove(XStudent student) {
		if (!isMaster())
			iLog.warn("Removing student on a slave node. That is suspicious.");
		Lock lock = writeLock();
		try {
			iStudentTable.getAdvancedCache().withFlags(Flag.IGNORE_RETURN_VALUES).remove(student.getStudentId());
		} finally {
			lock.release();
		}
	}

	@Override
	public void update(XStudent student, boolean updateRequests) {
		iLog.info("Update " + student + " with requests " + student.getRequests());
		if (!isMaster())
			iLog.warn("Updating student on a slave node. That is suspicious.");
		Lock lock = writeLock();
		try {
			iStudentTable.getAdvancedCache().withFlags(Flag.IGNORE_RETURN_VALUES).put(student.getStudentId(), student);
		} finally {
			lock.release();
		}
	}

	@Override
	public void remove(XOffering offering) {
		if (!isMaster())
			iLog.warn("Removing offering on a slave node. That is suspicious.");
		Lock lock = writeLock();
		try {
			iOfferingTable.getAdvancedCache().withFlags(Flag.IGNORE_RETURN_VALUES).remove(offering.getOfferingId());
			iExpectations.getAdvancedCache().withFlags(Flag.IGNORE_RETURN_VALUES).remove(offering.getOfferingId());
		} finally {
			lock.release();
		}
	}

	@Override
	public void update(XOffering offering) {
		if (!isMaster())
			iLog.warn("Updating offering on a slave node. That is suspicious.");
		Lock lock = writeLock();
		try {
			iOfferingTable.getAdvancedCache().withFlags(Flag.IGNORE_RETURN_VALUES).put(offering.getOfferingId(), offering);
		} finally {
			lock.release();
		}
	}

	@Override
	public void clearAll() {
		if (!isMaster())
			iLog.warn("Clearing all data on a slave node. That is suspicious.");
		Lock lock = writeLock();
		try {
			iStudentTable.clear();
			iOfferingTable.clear();
			iExpectations.clear();
		} finally {
			lock.release();
		}
	}

	@Override
	public void clearAllStudents() {
		if (!isMaster())
			iLog.warn("Clearing all students on a slave node. That is suspicious.");
		Lock lock = writeLock();
		try {
			iStudentTable.clear();
		} finally {
			lock.release();
		}
	}

	@Override
	public XCourseRequest assign(XCourseRequest request, XEnrollment enrollment) {
		iLog.info("Assign " + request + " with " + enrollment);
		if (!isMaster())
			iLog.warn("Assigning a request on a slave node. That is suspicious.");
		Lock lock = writeLock();
		try {
			XStudent student = iStudentTable.get(request.getStudentId());
			for (XRequest r: student.getRequests()) {
				if (r.equals(request)) {
					XCourseRequest cr = (XCourseRequest)r;

					// assign
					cr.setEnrollment(enrollment);
					
					iStudentTable.getAdvancedCache().withFlags(Flag.IGNORE_RETURN_VALUES).put(student.getStudentId(), student);
					return cr;
				}
			}
			iLog.warn("ASSIGN[3]: Request " + student + " " + request + " was not found among student requests");
			return null;
		} finally {
			lock.release();
		}
	}

	@Override
	public XCourseRequest waitlist(XCourseRequest request, boolean waitlist) {
		if (!isMaster())
			iLog.warn("Wait-listing a request on a slave node. That is suspicious.");
		Lock lock = writeLock();
		try {
			XStudent student = iStudentTable.get(request.getStudentId());
			for (XRequest r: student.getRequests()) {
				if (r.equals(request)) {
					XCourseRequest cr = (XCourseRequest)r;

					// assign
					cr.setWaitlist(waitlist);
					
					iStudentTable.getAdvancedCache().withFlags(Flag.IGNORE_RETURN_VALUES).put(student.getStudentId(), student);
					return cr;
				}
			}
			iLog.warn("WAITLIST[3]: Request " + student + " " + request + " was not found among student requests");
			return null;
		} finally {
			lock.release();
		}
	}

	public static class GetKeysCallable<T> implements DistributedCallable<Long, T, Collection<Long>>, Serializable {
		private static final long serialVersionUID = 1L;
		private transient Cache<Long, T> iCache;
		
		public GetKeysCallable() {
		}
		
		@Override
		public void setEnvironment(Cache<Long, T> cache, Set<Long> inputKeys) {
			iCache = cache;
		}

		@Override
		public Collection<Long> call() throws Exception {
			return new ArrayList<Long>(iCache.keySet());
		}
	}
	
	@Override
	public Lock lockStudent(Long studentId, Collection<Long> offeringIds, boolean excludeLockedOfferings) {
		if (!isMaster()) {
			iLog.warn("Failed to lock a student " + studentId + ": not executed on master.");
			return new NoLock();
		}
		return super.lockStudent(studentId, offeringIds, excludeLockedOfferings);
	}

	@Override
	public Lock lockOffering(Long offeringId, Collection<Long> studentIds, boolean excludeLockedOffering) {
		if (!isMaster()) {
			iLog.warn("Failed to lock an offering " + offeringId + ": not executed on master.");
			return new NoLock();
		}
		return super.lockOffering(offeringId, studentIds, excludeLockedOffering);
	}
	
	@Override
	public Lock lockRequest(CourseRequestInterface request) {
		if (!isMaster()) {
			iLog.warn("Failed to lock a request for student " + request.getStudentId() + ": not executed on master.");
			return new NoLock();
		}
		return super.lockRequest(request);
	}
	
	@Override
	public boolean isOfferingLocked(Long offeringId) {
		return iOfferingLocks.containsKey(offeringId);
	}

	@Override
	public void lockOffering(Long offeringId) {
		iOfferingLocks.getAdvancedCache().withFlags(Flag.IGNORE_RETURN_VALUES).put(offeringId, Boolean.TRUE);
	}

	@Override
	public void unlockOffering(Long offeringId) {
		iOfferingLocks.getAdvancedCache().withFlags(Flag.IGNORE_RETURN_VALUES).remove(offeringId);
	}
	
	@Override
	public Collection<Long> getLockedOfferings() {
		Lock lock = readLock();
		try {
			DistributedExecutorService ex = new DefaultExecutorService(iOfferingLocks);
			Set<Long> ret = new HashSet<Long>();
			
			List<Future<Collection<Long>>> futures = ex.submitEverywhere(new GetKeysCallable<Boolean>());
			for (Future<Collection<Long>> future: futures)
				ret.addAll(future.get());
			
			return ret;
		} catch (InterruptedException e) {
			throw new SectioningException(e.getMessage(), e);
		} catch (ExecutionException e) {
			throw new SectioningException(e.getMessage(), e);
		} finally {
			lock.release();
		}
	}
	
	@Override
	public void releaseAllOfferingLocks() {
		iOfferingLocks.clear();
	}
	
	private static class NoLock implements Lock {
		@Override
		public void release() {}
	}
	
	@Override
	public <E> E execute(OnlineSectioningAction<E> action, OnlineSectioningLog.Entity user) throws SectioningException {
		CheckMaster ch = action.getClass().getAnnotation(CheckMaster.class);
		if (ch != null && ch.value() == Master.REQUIRED && !isMaster())
			iLog.warn("Executing action " + action.name() + " (master required) on a slave node.");
		else if (ch != null && ch.value() == Master.AVOID && isMaster())
			iLog.warn("Executing action " + action.name() + " (avoid master) on a master node.");
		E ret = super.execute(action, user);
		return ret;
	}
	
	@Override
	public Lock writeLock() {
		if (!isMaster())
			iLog.warn("Asking for a WRITE lock on a slave node. That is suspicious.");
		return super.writeLock();
	}
	
	@Override
	public Lock lockAll() {
		if (!isMaster())
			iLog.warn("Asking for an ALL lock on a slave node. That is suspicious.");
		return super.lockAll();
	}
	
	@Listener(sync=true)
	public class OfferingTableListener {
		
		public OfferingTableListener(Collection<XOffering> offerings) {
			for (XOffering offering: offerings)
				addCourses(offering);
		}
		
		@CacheEntryModified
		public void modified(CacheEntryModifiedEvent<Long, XOffering> event) {
			if (event.isPre()) {
				if (event.getValue() != null)
					removeCourses(event.getValue());
			} else {
				if (event.getValue() != null)
					addCourses(event.getValue());
			}
		}
		
		@CacheEntryRemoved
		public void removed(CacheEntryRemovedEvent<Long, XOffering> event) {
			if (event.isPre())
				removeCourses(event.getValue());
		}
		
		public void removeCourses(XOffering offering) {
			Lock lock = writeLockIfNotHeld();
			try {
				for (XCourse course: offering.getCourses()) {
					iCourseForId.remove(course.getCourseId());
					XTreeSet<XCourseId> courses = iCourseForName.get(course.getCourseNameInLowerCase());
					if (courses != null) {
						courses.remove(course);
						if (courses.size() == 1) 
							for (XCourseId x: courses) x.setHasUniqueName(true);
						if (courses.isEmpty())
							iCourseForName.remove(course.getCourseNameInLowerCase());
					}
				}
			} finally {
				if (lock != null) lock.release();
			}
		}
		
		public void addCourses(XOffering offering) {
			Lock lock = writeLockIfNotHeld();
			try {
				for (XCourse course: offering.getCourses()) {
					iCourseForId.put(course.getCourseId(), new XCourseId(course));
					XTreeSet<XCourseId> courses = iCourseForName.get(course.getCourseNameInLowerCase());
					if (courses == null) {
						courses = new XTreeSet<XCourseId>(new XCourseId.XCourseIdSerializer());
						iCourseForName.put(course.getCourseNameInLowerCase(), courses);
					}
					courses.add(new XCourseId(course));
					if (courses.size() == 1) 
						for (XCourseId x: courses) x.setHasUniqueName(true);
					else if (courses.size() > 1)
						for (XCourseId x: courses) x.setHasUniqueName(false);
				}
			} finally {
				if (lock != null) lock.release();
			}
		}
	}
	
	@Listener(sync=true)
	public class StudentTableListener {
		public StudentTableListener(Collection<XStudent> students) {
			for (XStudent student: students)
				addRequests(student);
		}
		
		@CacheEntryModified
		public void modified(CacheEntryModifiedEvent<Long, XStudent> event) {
			if (event.isPre()) {
				if (event.getValue() != null)
					removeRequests(event.getValue());
			} else {
				if (event.getValue() != null)
					addRequests(event.getValue());
			}
		}
		
		@CacheEntryRemoved
		public void removed(CacheEntryRemovedEvent<Long, XStudent> event) {
			if (event.isPre())
				removeRequests(event.getValue());
		}
		
		public void removeRequests(XStudent oldStudent) {
			Lock lock = writeLockIfNotHeld();
			try {
				for (XRequest request: oldStudent.getRequests())
					if (request instanceof XCourseRequest)
						for (XCourseId course: ((XCourseRequest)request).getCourseIds()) {
							XHashSet<XCourseRequest> requests = iOfferingRequests.get(course.getOfferingId());
							if (requests != null) {
								if (!requests.remove(request))
									iLog.warn("UPDATE[1]: Request " + oldStudent + " " + request + " was not present in the offering requests table for " + course);
								iOfferingRequests.put(course.getOfferingId(), requests);
							} else {
								iLog.warn("UPDATE[2]: Request " + oldStudent + " " + request + " was not present in the offering requests table for " + course);
							}
						}
			} finally {
				if (lock != null) lock.release();
			}
		}
		
		public void addRequests(XStudent student) {
			Lock lock = writeLockIfNotHeld();
			try {
				for (XRequest request: student.getRequests())
					if (request instanceof XCourseRequest)
						for (XCourseId course: ((XCourseRequest)request).getCourseIds()) {
							XHashSet<XCourseRequest> requests = iOfferingRequests.get(course.getOfferingId());
							if (requests == null)
								requests = new XHashSet<XCourseRequest>(new XCourseRequest.XCourseRequestSerializer());
							requests.add((XCourseRequest)request);
							iOfferingRequests.put(course.getOfferingId(), requests);
						}
			} finally {
				if (lock != null) lock.release();
			}
		}
	}
}
