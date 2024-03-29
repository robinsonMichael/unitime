/*
 * UniTime 3.4 - 3.5 (University Timetabling Application)
 * Copyright (C) 2012 - 2013, UniTime LLC, and individual contributors
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
package org.unitime.timetable.gwt.client.events;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.unitime.timetable.gwt.client.widgets.FilterBox;
import org.unitime.timetable.gwt.client.widgets.FilterBox.Chip;
import org.unitime.timetable.gwt.client.widgets.FilterBox.Suggestion;
import org.unitime.timetable.gwt.resources.GwtAriaMessages;
import org.unitime.timetable.gwt.resources.GwtMessages;
import org.unitime.timetable.gwt.shared.AcademicSessionProvider;
import org.unitime.timetable.gwt.shared.EventInterface.FilterRpcResponse;
import org.unitime.timetable.gwt.shared.EventInterface.RoomFilterRpcRequest;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;

/**
 * @author Tomas Muller
 */
public class RoomFilterBox extends UniTimeFilterBox<RoomFilterRpcRequest> {
	private static final GwtMessages MESSAGES = GWT.create(GwtMessages.class);
	private static GwtAriaMessages ARIA = GWT.create(GwtAriaMessages.class);
	private ListBox iBuildings, iDepartments;
	private TextBox iMin, iMax;
	private Chip iLastSize = null;
	
	public RoomFilterBox(AcademicSessionProvider session) {
		super(session);
		
		iDepartments = new ListBox(false);
		iDepartments.setWidth("100%");
		
		addFilter(new FilterBox.CustomFilter("department", iDepartments) {
			@Override
			public void getSuggestions(List<Chip> chips, String text, AsyncCallback<Collection<Suggestion>> callback) {
				if (text.isEmpty()) {
					callback.onSuccess(null);
				} else {
					Chip oldChip = getChip("department");
					List<Suggestion> suggestions = new ArrayList<Suggestion>();
					for (int i = 0; i < iDepartments.getItemCount(); i++) {
						Chip chip = new Chip("department", iDepartments.getValue(i));
						String name = iDepartments.getItemText(i);
						if (iDepartments.getValue(i).toLowerCase().startsWith(text.toLowerCase())) {
							suggestions.add(new Suggestion(name, chip, oldChip));
						} else if (text.length() > 2 && (name.toLowerCase().contains(" " + text.toLowerCase()) || name.toLowerCase().contains(" (" + text.toLowerCase()))) {
							suggestions.add(new Suggestion(name, chip, oldChip));
						}
					}
					if ("department".startsWith(text.toLowerCase()) && text.toLowerCase().length() >= 5) {
						for (int i = 0; i < iDepartments.getItemCount(); i++) {
							Chip chip = new Chip("department", iDepartments.getValue(i));
							String name = iDepartments.getItemText(i);
							if (!chip.equals(oldChip))
								suggestions.add(new Suggestion(name, chip, oldChip));
						}
					}
					callback.onSuccess(suggestions);
				}
			}
		});
		iDepartments.addChangeHandler(new ChangeHandler() {
			@Override
			public void onChange(ChangeEvent event) {
				Chip oldChip = getChip("department");
				Chip newChip = (iDepartments.getSelectedIndex() <= 0 ? null : new Chip("department", iDepartments.getValue(iDepartments.getSelectedIndex())));
				if (oldChip != null) {
					if (newChip == null) {
						removeChip(oldChip, true);
					} else {
						if (!oldChip.getValue().equals(newChip.getValue())) {
							removeChip(oldChip, false);
							addChip(newChip, true);
						}
					}
				} else {
					if (newChip != null)
						addChip(newChip, true);
				}
			}
		});
		
		addFilter(new FilterBox.StaticSimpleFilter("type"));
		addFilter(new FilterBox.StaticSimpleFilter("feature"));
		addFilter(new FilterBox.StaticSimpleFilter("group"));
		addFilter(new FilterBox.StaticSimpleFilter("size"));
		addFilter(new FilterBox.StaticSimpleFilter("flag"));
		
		iBuildings = new ListBox(true);
		iBuildings.setWidth("100%"); iBuildings.setVisibleItemCount(3);
		
		addFilter(new FilterBox.CustomFilter("building", iBuildings) {
			@Override
			public void getSuggestions(List<Chip> chips, String text, AsyncCallback<Collection<Suggestion>> callback) {
				if (text.isEmpty()) {
					callback.onSuccess(null);
				} else {
					List<Suggestion> suggestions = new ArrayList<Suggestion>();
					for (int i = 0; i < iBuildings.getItemCount(); i++) {
						Chip chip = new Chip("building", iBuildings.getValue(i));
						String name = iBuildings.getItemText(i);
						if (iBuildings.getValue(i).toLowerCase().startsWith(text.toLowerCase())) {
							suggestions.add(new Suggestion(name, chip));
						} else if (text.length() > 2 && name.toLowerCase().contains(" " + text.toLowerCase())) {
							suggestions.add(new Suggestion(name, chip));
						}
					}
					if ("building".startsWith(text.toLowerCase()) && text.toLowerCase().length() >= 5) {
						for (int i = 0; i < iBuildings.getItemCount(); i++) {
							Chip chip = new Chip("building", iBuildings.getValue(i));
							String name = iBuildings.getItemText(i);
							suggestions.add(new Suggestion(name, chip));
						}
					}
					callback.onSuccess(suggestions);
				}
			}
			@Override
			public boolean isVisible() {
				return iBuildings.getItemCount() > 0;
			}
		});
		iBuildings.addChangeHandler(new ChangeHandler() {
			@Override
			public void onChange(ChangeEvent event) {
				boolean changed = false;
				for (int i = 0; i < iBuildings.getItemCount(); i++) {
					Chip chip = new Chip("building", iBuildings.getValue(i));
					if (iBuildings.isItemSelected(i)) {
						if (!hasChip(chip)) {
							addChip(chip, false); changed = true;
						}
					} else {
						if (hasChip(chip)) {
							removeChip(chip, false); changed = true;
						}
					}
				}
				if (changed)
					fireValueChangeEvent();
			}
		});
		
		Label l1 = new Label(MESSAGES.propMin());

		iMin = new TextBox();
		iMin.setStyleName("unitime-TextArea");
		iMin.setMaxLength(10); iMin.getElement().getStyle().setWidth(50, Unit.PX);
		
		Label l2 = new Label(MESSAGES.propMax());
		l2.getElement().getStyle().setMarginLeft(10, Unit.PX);

		iMax = new TextBox();
		iMax.setMaxLength(10); iMax.getElement().getStyle().setWidth(50, Unit.PX);
		iMax.setStyleName("unitime-TextArea");
		
		final CheckBox events = new CheckBox(MESSAGES.checkOnlyEventLocations());
		events.getElement().getStyle().setMarginLeft(10, Unit.PX);
		
		final CheckBox nearby = new CheckBox(MESSAGES.checkIncludeNearby());
		nearby.getElement().getStyle().setMarginLeft(10, Unit.PX);
		
		addFilter(new FilterBox.CustomFilter("other", l1, iMin, l2, iMax, events, nearby) {
			@Override
			public void getSuggestions(final List<Chip> chips, final String text, AsyncCallback<Collection<FilterBox.Suggestion>> callback) {
				if (text.isEmpty()) {
					callback.onSuccess(null);
				} else {
					List<FilterBox.Suggestion> suggestions = new ArrayList<FilterBox.Suggestion>();
					if ("nearby".startsWith(text.toLowerCase()) || MESSAGES.checkIncludeNearby().toLowerCase().startsWith(text.toLowerCase())) {
						suggestions.add(new Suggestion(MESSAGES.checkIncludeNearby(), new Chip("flag", "Nearby")));
					} else if ("all".startsWith(text.toLowerCase()) || MESSAGES.checkAllLocations().toLowerCase().startsWith(text.toLowerCase())) {
						suggestions.add(new Suggestion(MESSAGES.checkAllLocations(), new Chip("flag", "All"), new Chip("flag", "Event")));
					} else if ("event".startsWith(text.toLowerCase()) || MESSAGES.checkOnlyEventLocations().toLowerCase().startsWith(text.toLowerCase())) {
						suggestions.add(new Suggestion(MESSAGES.checkOnlyEventLocations(), new Chip("flag", "Event"), new Chip("flag", "All")));
					} else {
						Chip old = null;
						for (Chip c: chips) { if (c.getCommand().equals("size")) { old = c; break; } }
						try {
							String number = text;
							String prefix = "";
							if (text.startsWith("<=") || text.startsWith(">=")) { number = number.substring(2); prefix = text.substring(0, 2); }
							else if (text.startsWith("<") || text.startsWith(">")) { number = number.substring(1); prefix = text.substring(0, 1); }
							Integer.parseInt(number);
							suggestions.add(new Suggestion(new Chip("size", text), old));
							if (prefix.isEmpty()) {
								suggestions.add(new Suggestion(new Chip("size", "<=" + text), old));
								suggestions.add(new Suggestion(new Chip("size", ">=" + text), old));
							}
						} catch (Exception e) {}
						if (text.contains("..")) {
							try {
								String first = text.substring(0, text.indexOf('.'));
								String second = text.substring(text.indexOf("..") + 2);
								Integer.parseInt(first); Integer.parseInt(second);
								suggestions.add(new Suggestion(new Chip("size", text), old));
							} catch (Exception e) {}
						}
					}
					callback.onSuccess(suggestions);
				}
			}

		}); 

		iMin.addChangeHandler(new ChangeHandler() {
			@Override
			public void onChange(ChangeEvent event) {
				sizeChanged(true);
			}
		});
		iMax.addChangeHandler(new ChangeHandler() {
			@Override
			public void onChange(ChangeEvent event) {
				sizeChanged(true);
			}
		});
		
		iMin.addKeyPressHandler(new KeyPressHandler() {
			@Override
			public void onKeyPress(KeyPressEvent event) {
				Scheduler.get().scheduleDeferred(new ScheduledCommand() {
					@Override
					public void execute() {
						sizeChanged(false);
					}
				});
			}
		});
		iMax.addKeyPressHandler(new KeyPressHandler() {
			@Override
			public void onKeyPress(KeyPressEvent event) {
				Scheduler.get().scheduleDeferred(new ScheduledCommand() {
					@Override
					public void execute() {
						sizeChanged(false);
					}
				});
			}
		});
		
		iMin.addKeyUpHandler(new KeyUpHandler() {
			@Override
			public void onKeyUp(KeyUpEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_BACKSPACE)
					Scheduler.get().scheduleDeferred(new ScheduledCommand() {
						@Override
						public void execute() {
							sizeChanged(false);
						}
					});
			}
		});
		iMax.addKeyUpHandler(new KeyUpHandler() {
			@Override
			public void onKeyUp(KeyUpEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_BACKSPACE)
					Scheduler.get().scheduleDeferred(new ScheduledCommand() {
						@Override
						public void execute() {
							sizeChanged(false);
						}
					});
			}
		});
		iMin.addBlurHandler(new BlurHandler() {
			@Override
			public void onBlur(BlurEvent event) {
				sizeChanged(true);
			}
		});
		iMax.addBlurHandler(new BlurHandler() {
			@Override
			public void onBlur(BlurEvent event) {
				sizeChanged(true);
			}
		});
		
		nearby.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
			@Override
			public void onValueChange(ValueChangeEvent<Boolean> event) {
				Chip chip = new Chip("flag", "Nearby");
				if (event.getValue()) {
					if (!hasChip(chip)) addChip(chip, true);
				} else {
					if (hasChip(chip)) removeChip(chip, true);
				}
			}
		});
		nearby.addMouseDownHandler(new MouseDownHandler() {
			@Override
			public void onMouseDown(MouseDownEvent event) {
				event.getNativeEvent().stopPropagation();
				event.getNativeEvent().preventDefault();
			}
		});
		
		events.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
			@Override
			public void onValueChange(ValueChangeEvent<Boolean> event) {
				Chip eventChip = new Chip("flag", "Event");
				Chip allChip = new Chip("flag", "All");
				if (event.getValue()) {
					if (!hasChip(eventChip)) addChip(eventChip, true);
					if (hasChip(allChip)) removeChip(allChip, true);
				} else {
					if (hasChip(eventChip)) removeChip(eventChip, true);
					if (!hasChip(allChip)) addChip(allChip, true);
				}
			}
		});
		events.addMouseDownHandler(new MouseDownHandler() {
			@Override
			public void onMouseDown(MouseDownEvent event) {
				event.getNativeEvent().stopPropagation();
				event.getNativeEvent().preventDefault();
			}
		});

		addValueChangeHandler(new ValueChangeHandler<String>() {
			@Override
			public void onValueChange(ValueChangeEvent<String> event) {
				iLastSize = getChip("size");
				if (!isFilterPopupShowing()) {
					nearby.setValue(hasChip(new Chip("flag", "Nearby")));
					events.setValue(hasChip(new Chip("flag", "Event")));
					Chip size = getChip("size");
					if (size != null) {
						if (size.getValue().startsWith("<=")) {
							iMin.setText(""); iMax.setText(size.getValue().substring(2));
						} else if (size.getValue().startsWith("<")) {
							try {
								iMax.setText(String.valueOf(Integer.parseInt(size.getValue().substring(1)) - 1)); iMin.setText("");							
							} catch (Exception e) {}
						} else if (size.getValue().startsWith(">=")) {
							iMin.setText(size.getValue().substring(2)); iMax.setText("");
						} else if (size.getValue().startsWith(">")) {
							try {
								iMin.setText(String.valueOf(Integer.parseInt(size.getValue().substring(1)) + 1)); iMax.setText("");							
							} catch (Exception e) {}
						} else if (size.getValue().contains("..")) {
							iMin.setText(size.getValue().substring(0, size.getValue().indexOf(".."))); iMax.setText(size.getValue().substring(size.getValue().indexOf("..") + 2));
						} else {
							iMin.setText(size.getValue()); iMax.setText(size.getValue());
						}
					} else {
						iMin.setText(""); iMax.setText("");
					}
					for (int i = 0; i < iBuildings.getItemCount(); i++) {
						String value = iBuildings.getValue(i);
						iBuildings.setItemSelected(i, hasChip(new Chip("building", value)));
					}
					iDepartments.setSelectedIndex(0);
					for (int i = 1; i < iDepartments.getItemCount(); i++) {
						String value = iDepartments.getValue(i);
						if (hasChip(new Chip("department", value))) {
							iDepartments.setSelectedIndex(i);
							break;
						}
					}
				}
				if (getAcademicSessionId() != null)
					init(false, getAcademicSessionId(), new Command() {
						@Override
						public void execute() {
							if (isFilterPopupShowing())
								showFilterPopup();
						}
					});
				setAriaLabel(ARIA.roomFilter(toAriaString()));
			}
		});
		
		addFocusHandler(new FocusHandler() {
			@Override
			public void onFocus(FocusEvent event) {
				setAriaLabel(ARIA.roomFilter(toAriaString()));
			}
		});
	}
	
	@Override
	protected void onLoad(FilterRpcResponse result) {
		if (!result.hasEntities()) return;
		boolean added = false;
		types: for (String type: result.getTypes()) {
			for (FilterBox.Filter filter: iFilter.getWidget().getFilters()) {
				if (filter.getCommand().equals(type)) continue types;
			}
			iFilter.getWidget().getFilters().add(iFilter.getWidget().getFilters().size() - 5, new FilterBox.StaticSimpleFilter(type));
			added = true;
		}
		if (added) setValue(getValue(), false);
	}
	
	@Override
	protected boolean populateFilter(FilterBox.Filter filter, List<FilterRpcResponse.Entity> entities) {
		if ("building".equals(filter.getCommand())) {
			iBuildings.clear();
			if (entities != null)
				for (FilterRpcResponse.Entity entity: entities)
					iBuildings.addItem(entity.getName() + " (" + entity.getCount() + ")", entity.getAbbreviation());
			for (int i = 0; i < iBuildings.getItemCount(); i++) {
				String value = iBuildings.getValue(i);
				iBuildings.setItemSelected(i, hasChip(new Chip("building", value)));
			}
			return true;
		} else if ("department".equals(filter.getCommand())) {
			iDepartments.clear();
			iDepartments.addItem(MESSAGES.itemAllDepartments(), "");
			if (entities != null)
				for (FilterRpcResponse.Entity entity: entities)
					iDepartments.addItem(entity.getName() + " (" + entity.getCount() + ")", entity.getAbbreviation());
			
			iDepartments.setSelectedIndex(0);
			Chip dept = getChip("department");
			if (dept != null)
				for (int i = 1; i < iDepartments.getItemCount(); i++)
					if (dept.getValue().equals(iDepartments.getValue(i))) {
						iDepartments.setSelectedIndex(i);
						break;
					}
			return true;
		} else 
			return super.populateFilter(filter, entities);
	}
	
	private void sizeChanged(boolean fireChange) {
		Chip oldChip = getChip("size");
		if (iMin.getText().isEmpty() && iMax.getText().isEmpty()) {
			if (oldChip != null) {
				removeChip(oldChip, fireChange);
			}
		} else {
			Chip newChip = new Chip("size", iMin.getText().isEmpty() ? "<=" + iMax.getText() : iMax.getText().isEmpty() ? ">=" + iMin.getText() : iMin.getText() + ".." + iMax.getText());
			if (newChip.equals(oldChip)) {
				if (fireChange && !newChip.equals(iLastSize)) fireValueChangeEvent();
				return;
			} else {
				if (oldChip != null)
					removeChip(oldChip, false);
				addChip(newChip, fireChange);
			}
		}
	}
	
	@Override
	public RoomFilterRpcRequest createRpcRequest() {
		return new RoomFilterRpcRequest();
	}
}