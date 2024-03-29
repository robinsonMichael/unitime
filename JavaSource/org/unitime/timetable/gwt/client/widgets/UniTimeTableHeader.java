/*
 * UniTime 3.2 - 3.5 (University Timetabling Application)
 * Copyright (C) 2010 - 2013, UniTime LLC, and individual contributors
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
package org.unitime.timetable.gwt.client.widgets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.unitime.timetable.gwt.client.widgets.UniTimeTable.HasAdditionalStyleNames;
import org.unitime.timetable.gwt.client.widgets.UniTimeTable.HasCellAlignment;
import org.unitime.timetable.gwt.client.widgets.UniTimeTable.HasColSpan;
import org.unitime.timetable.gwt.client.widgets.UniTimeTable.HasColumn;
import org.unitime.timetable.gwt.client.widgets.UniTimeTable.HasStyleName;

import com.google.gwt.aria.client.Roles;
import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;

/**
 * @author Tomas Muller
 */
public class UniTimeTableHeader extends HTML implements HasStyleName, HasCellAlignment, HasColSpan, HasAdditionalStyleNames, HasColumn {
	private int iColSpan = 1, iColumn = -1;
	private HorizontalAlignmentConstant iAlign;
	private List<Operation> iOperations = new ArrayList<Operation>();
	private List<String> iStyleNames = new ArrayList<String>();
	private String iTitle = null;
	private Boolean iOrder = null;
	private ClickHandler iClickHandler = null;
	
	public UniTimeTableHeader(String title, int colSpan, HorizontalAlignmentConstant align) {
		super(title, false);
		iColSpan = colSpan;
		iAlign = align;
		iTitle = title;
		
		addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				final PopupPanel popup = new PopupPanel(true);
				popup.addStyleName("unitime-Menu");
				if (!setMenu(popup)) return;
				popup.showRelativeTo((Widget)event.getSource());
				((MenuBar)popup.getWidget()).focus();
			}
		});
	}
	
	public UniTimeTableHeader(String title, int colSpan, HorizontalAlignmentConstant align, ClickHandler clickHandler) {
		super(title, false);
		iColSpan = colSpan;
		iAlign = align;
		iTitle = title;
		
		iClickHandler = clickHandler;
		addClickHandler(clickHandler);
	}
	
	public boolean setMenu(final PopupPanel popup) {
		List<Operation> operations = getOperations();
		if (operations.isEmpty()) return false;
		boolean first = true;
		MenuBar menu = new MenuBarWithAccessKeys();
		for (final Operation op: operations) {
			if (!op.isApplicable()) continue;
			if (op.hasSeparator() && !first)
				menu.addSeparator();
			first = false;
			MenuItem item = new MenuItem(op.getName(), true, new Command() {
				@Override
				public void execute() {
					popup.hide();
					op.execute();
				}
			});
			if (op instanceof AriaOperation)
				Roles.getMenuitemRole().setAriaLabelProperty(item.getElement(), ((AriaOperation)op).getAriaLabel());
			else
				Roles.getMenuitemRole().setAriaLabelProperty(item.getElement(), UniTimeHeaderPanel.stripAccessKey(op.getName()));
			menu.addItem(item);
		}
		if (first) return false;
		menu.setVisible(true);
		popup.add(menu);
		return true;
	}
	
	public UniTimeTableHeader(String title) {
		this(title, 1, HasHorizontalAlignment.ALIGN_LEFT);
	}
	
	public UniTimeTableHeader(String title, int colSpan) {
		this(title, colSpan, HasHorizontalAlignment.ALIGN_LEFT);
	}
	
	public UniTimeTableHeader() {
		this(" ", 1, HasHorizontalAlignment.ALIGN_LEFT);
	}

	public UniTimeTableHeader(String title, ClickHandler clickHandler) {
		this(title, 1, HasHorizontalAlignment.ALIGN_LEFT, clickHandler);
	}
	
	public UniTimeTableHeader(String title, int colSpan, ClickHandler clickHandler) {
		this(title, colSpan, HasHorizontalAlignment.ALIGN_LEFT, clickHandler);
	}
	
	public UniTimeTableHeader(ClickHandler clickHandler) {
		this(" ", 1, HasHorizontalAlignment.ALIGN_LEFT, clickHandler);
	}

	
	public UniTimeTableHeader(String title, HorizontalAlignmentConstant align) {
		this(title, 1, align);
	}

	public int getColSpan() {
		return iColSpan;
	}
	
	public HorizontalAlignmentConstant getCellAlignment() {
		return iAlign;
	}
	
	public String getStyleName() {
		return (iOperations.isEmpty() && iClickHandler == null ? "unitime-TableHeader" : "unitime-ClickableTableHeader");
	}
	

	public void addOperation(Operation operation) {
		iOperations.add(operation);
	}
	
	public List<Operation> getOperations() {
		return iOperations;
	}
	
	public static interface Operation extends Command {
		public String getName();
		public boolean isApplicable();
		public boolean hasSeparator();
	}
	
	public static interface AriaOperation extends Operation {
		public String getAriaLabel();
	}
	
	public String getHint() {
		return null;
	}
	
	public List<String> getAdditionalStyleNames() {
		return iStyleNames;
	}
	
	public void addAdditionalStyleName(String styleName) {
		iStyleNames.add(styleName);
	}

	@Override
	public int getColumn() {
		return iColumn;
	}

	@Override
	public void setColumn(int column) {
		iColumn = column;
	}
	
	@Override
	public void setHTML(String html) {
		iTitle = html;
		super.setHTML(html);
	}
	
	@Override
	public String getHTML() {
		return iTitle;
	}
	
	public void setOrder(Boolean order) {
		iOrder = order;
		super.setHTML(order == null ? iTitle : order ? "&uarr; " + iTitle : "&darr; " + iTitle);
	}
	
	public Boolean getOrder() {
		return iOrder;
	}
	
	public static class MenuBarWithAccessKeys extends MenuBar {
		private Map<Character, MenuItem> iAccessKeys = new HashMap<Character, MenuItem>();
		
		public MenuBarWithAccessKeys() {
			super(true);
			setFocusOnHoverEnabled(true);
			sinkEvents(Event.ONKEYPRESS);
		}
		
		@Override
		public MenuItem addItem(MenuItem item) {
			Character ch = UniTimeHeaderPanel.guessAccessKey(item.getHTML());
			if (ch != null)
				iAccessKeys.put(Character.toLowerCase(ch), item);
			item.getElement().getStyle().setCursor(Cursor.POINTER);
			return super.addItem(item);
		}
		
		@Override
		public void onBrowserEvent(Event event) {
			switch (DOM.eventGetType(event)) {
			case Event.ONKEYPRESS:
				MenuItem item = iAccessKeys.get(Character.toLowerCase((char)event.getCharCode()));
				if (item != null)
					item.getScheduledCommand().execute();
			}
			super.onBrowserEvent(event);
			
		}
	}
}
