package com.cloudcraftgaming.discal.module.command;

import com.cloudcraftgaming.discal.database.DatabaseManager;
import com.cloudcraftgaming.discal.internal.calendar.CalendarAuth;
import com.cloudcraftgaming.discal.internal.calendar.event.*;
import com.cloudcraftgaming.discal.internal.data.CalendarData;
import com.cloudcraftgaming.discal.module.command.info.CommandInfo;
import com.cloudcraftgaming.discal.utils.EventColor;
import com.cloudcraftgaming.discal.utils.Message;
import com.cloudcraftgaming.discal.utils.PermissionChecker;
import com.cloudcraftgaming.discal.utils.Validator;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by Nova Fox on 1/3/2017.
 * Website: www.cloudcraftgaming.com
 * For Project: DisCal
 */
@SuppressWarnings("Duplicates")
public class EventCommand implements ICommand {
    /**
     * Gets the command this Object is responsible for.
     * @return The command this Object is responsible for.
     */
    @Override
    public String getCommand() {
        return "event";
    }

    /**
     * Gets the short aliases of the command this object is responsible for.
     * </br>
     * This will return an empty ArrayList if none are present
     *
     * @return The aliases of the command.
     */
    @Override
    public ArrayList<String> getAliases() {
        return new ArrayList<>();
    }

    /**
     * Gets the info on the command (not sub command) to be used in help menus.
     *
     * @return The command info.
     */
    @Override
    public CommandInfo getCommandInfo() {
        CommandInfo info = new CommandInfo("event");
        info.setDescription("Used for all event related functions");
        info.setExample("!event <function> (value(s))");

        info.getSubCommands().add("create");
        info.getSubCommands().add("copy");
        info.getSubCommands().add("cancel");
        info.getSubCommands().add("delete");
        info.getSubCommands().add("view");
        info.getSubCommands().add("review");
        info.getSubCommands().add("confirm");
        info.getSubCommands().add("start");
        info.getSubCommands().add("startDate");
        info.getSubCommands().add("end");
        info.getSubCommands().add("endDate");
        info.getSubCommands().add("summary");
        info.getSubCommands().add("description");
        info.getSubCommands().add("color");

        return info;
    }

    /**
     * Issues the command this Object is responsible for.
     * @param args The command arguments.
     * @param event The event received.
     * @param client The Client associated with the Bot.
     * @return <code>true</code> if successful, else <code>false</code>.
     */
    @Override
    public Boolean issueCommand(String[] args, MessageReceivedEvent event, IDiscordClient client) {
        String guildId = event.getMessage().getGuild().getID();
        //TODO: Add multiple calendar handling.
        CalendarData calendarData = DatabaseManager.getManager().getMainCalendar(guildId);
        if (PermissionChecker.hasSufficientRole(event)) {
            if (args.length < 1) {
                Message.sendMessage("Please specify the function you would like to execute.", event, client);
            } else if (args.length == 1) {
                String function = args[0];
                if (function.equalsIgnoreCase("create")) {
                    if (EventCreator.getCreator().hasPreEvent(guildId)) {
                        Message.sendMessage("Event Creator already started!", event, client);
                    } else {
                        if (!calendarData.getCalendarAddress().equalsIgnoreCase("primary")) {
                            EventCreator.getCreator().init(event);
                            Message.sendMessage("Event Creator initiated! Please specify event summary.", event, client);
                        } else {
                            Message.sendMessage("You cannot create an event when you do not have a calendar!", event, client);
                        }
                    }
                } else if (function.equalsIgnoreCase("copy")) {
                    if (EventCreator.getCreator().hasPreEvent(guildId)) {
                        Message.sendMessage("Event Creator already started!", event, client);
                    } else {
                        if (!calendarData.getCalendarAddress().equalsIgnoreCase("primary")) {
                            Message.sendMessage("Please specify the ID of the event you wish to copy with `!event copy <ID>`", event, client);
                        } else {
                            Message.sendMessage("You cannot copy an event when you do not have a calendar", event, client);
                        }
                    }
                } else if (function.equalsIgnoreCase("cancel")) {
                    if (EventCreator.getCreator().terminate(event)) {
                        Message.sendMessage("Event creation canceled! Event creator terminated!", event, client);
                    } else {
                        Message.sendMessage("Event Creation could not be cancelled because it was never started!", event, client);
                    }
                } else if (function.equalsIgnoreCase("view") || function.equalsIgnoreCase("review")) {
                    if (EventCreator.getCreator().hasPreEvent(guildId)) {
                        Message.sendMessage(EventMessageFormatter.getPreEventEmbed(EventCreator.getCreator().getPreEvent(guildId)), "Confirm event `!event confirm` to add to calendar OR edit the values!", event, client);
                    } else {
                        Message.sendMessage("To review an event you must have the event creator initialized OR use `!event view <event ID>` to view an event in the calendar!", event, client);
                    }
                } else if (function.equalsIgnoreCase("confirm")) {
                    if (EventCreator.getCreator().hasPreEvent(guildId)) {
                        if (EventCreator.getCreator().getPreEvent(guildId).hasRequiredValues()) {
                            if (!calendarData.getCalendarAddress().equalsIgnoreCase("primary")) {
                                EventCreatorResponse response = EventCreator.getCreator().confirmEvent(event);
                                if (response.isSuccessful()) {
                                    Message.sendMessage(EventMessageFormatter.getEventConfirmationEmbed(response), "Event confirmed!", event, client);
                                } else {
                                    Message.sendMessage("Event created failed!", event, client);
                                }
                            } else {
                                Message.sendMessage("You cannot confirm an event when you do not have a calendar!", event, client);
                            }
                        } else {
                            Message.sendMessage("Required data not set! Please review event with `!event review`", event, client);
                        }
                    } else {
                        Message.sendMessage("Event Creator has not been initialized! Create an event to initialize!", event, client);
                    }
                } else if (function.equalsIgnoreCase("delete")) {
                    if (!EventCreator.getCreator().hasPreEvent(guildId)) {
                        Message.sendMessage("Please specify the Id of the event to delete!", event, client);
                    } else {
                        Message.sendMessage("You cannot delete an event while in the creator!", event, client);
                    }
                }
            } else if (args.length == 2) {
                String function = args[0];
                if (function.equalsIgnoreCase("create")) {
                    if (EventCreator.getCreator().hasPreEvent(guildId)) {
                        Message.sendMessage("Event Creator already started!", event, client);
                    } else {
                        if (!calendarData.getCalendarAddress().equalsIgnoreCase("primary")) {
                            EventCreator.getCreator().init(event);
                            Message.sendMessage("Event Creator initiated! Please specify event summary.", event, client);
                        } else {
                            Message.sendMessage("You cannot create an event when you do not have a calendar!", event, client);
                        }
                    }
                } else if (function.equalsIgnoreCase("copy")) {
                    if (EventCreator.getCreator().hasPreEvent(guildId)) {
                        Message.sendMessage("Event Creator already started!", event, client);
                    } else {
                        if (!calendarData.getCalendarAddress().equalsIgnoreCase("primary")) {
                            String eventId = args[1];
                            //Check if valid.
                            if (EventUtils.eventExists(guildId, eventId)) {
                                PreEvent preEvent = EventCreator.getCreator().init(event, eventId);
                                if (preEvent != null) {
                                    Message.sendMessage(EventMessageFormatter.getPreEventEmbed(preEvent), "Event Creator initialized! Event details copied! Please specify the date/times!", event, client);
                                } else {
                                    Message.sendMessage("Something went wrong! I'm sorry, try again!", event, client);
                                }
                            } else {
                                Message.sendMessage("I can't find that event! Are you sure the ID is correct?", event, client);
                            }
                        } else {
                            Message.sendMessage("You cannot cop an event when you do not have a calendar!", event, client);
                        }
                    }
                } else if (function.equalsIgnoreCase("view")) {
                    if (!EventCreator.getCreator().hasPreEvent(guildId)) {
                        //Try to get the event by ID.
                        try {
                            Calendar service = CalendarAuth.getCalendarService();
                            Event calEvent = service.events().get(calendarData.getCalendarAddress(), args[1]).execute();
                            Message.sendMessage(EventMessageFormatter.getEventEmbed(calEvent, guildId), event, client);
                        } catch (IOException e) {
                            //Event probably doesn't exist...
                            Message.sendMessage("Oops! Something went wrong! Are you sure the event ID is correct?", event, client);
                        }
                    } else {
                        Message.sendMessage("The event creator is active! You cannot view another event while the creator is active!", event, client);
                    }
                } else if (function.equalsIgnoreCase("delete")) {
                    if (!EventCreator.getCreator().hasPreEvent(guildId)) {
                        if (EventUtils.deleteEvent(guildId, args[1])) {
                            Message.sendMessage("Event successfully deleted!", event, client);
                        } else {
                            Message.sendMessage("Failed to delete event! Is the Event ID correct?", event, client);
                        }
                    } else {
                        Message.sendMessage("You cannot delete an event while in the creator!", event, client);
                    }
                } else if (function.equalsIgnoreCase("startDate") || function.equalsIgnoreCase("start")) {
                    if (EventCreator.getCreator().hasPreEvent(guildId)) {
                        String dateRaw = args[1].trim();
                        if (dateRaw.length() > 10) {
                            try {
                                //Do a lot of date shuffling to get to proper formats and shit like that.
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss");
                                TimeZone tz = TimeZone.getTimeZone(EventCreator.getCreator().getPreEvent(guildId).getTimeZone());
                                sdf.setTimeZone(tz);
                                Date dateObj = sdf.parse(dateRaw);
                                DateTime dateTime = new DateTime(dateObj);
                                EventDateTime eventDateTime = new EventDateTime();
                                eventDateTime.setDateTime(dateTime);

                                //Wait! Lets check now if its in the future and not the past!
                                if (!Validator.inPast(dateRaw, tz) && !Validator.startAfterEnd(dateRaw, tz, EventCreator.getCreator().getPreEvent(guildId))) {
                                    //Date shuffling done, now actually apply all that damn stuff here.
                                    EventCreator.getCreator().getPreEvent(guildId).setStartDateTime(eventDateTime);

                                    //Apply viewable date/times...
                                    SimpleDateFormat sdfV = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss");
                                    Date dateObjV = sdfV.parse(dateRaw);
                                    DateTime dateTimeV = new DateTime(dateObjV);
                                    EventDateTime eventDateTimeV = new EventDateTime();
                                    eventDateTimeV.setDateTime(dateTimeV);
                                    EventCreator.getCreator().getPreEvent(guildId).setViewableStartDate(eventDateTimeV);

                                    Message.sendMessage("Event start date (yyyy/MM/dd) set to: `" +
                                            EventMessageFormatter.getHumanReadableDate(eventDateTimeV) + "`"
                                            + Message.lineBreak
                                            + "Event start time (HH:mm) set to: `"
                                            + EventMessageFormatter.getHumanReadableTime(eventDateTimeV) + "`"
                                            + Message.lineBreak + Message.lineBreak
                                            + "Please specify the following: "
                                            + Message.lineBreak
                                            + "End date & ending time(military) in `yyyy/MM/dd-HH:mm:ss` format with the command `!event end <DateAndTime>`", event, client);
                                } else {
                                    //Oops! Time is in the past or after end...
                                    Message.sendMessage("Sorry >.< but I can't schedule an event that is in the past or has a starting time that is after the ending time!!! Please make sure you typed everything correctly.", event, client);
                                }
                            } catch (ParseException e) {
                                Message.sendMessage("Invalid Date & Time specified!", event, client);
                            }
                        } else {
                            Message.sendMessage("Invalid date/time format! Use `yyyy/MM/dd-HH:mm:ss`", event, client);
                        }
                    } else {
                        Message.sendMessage("Event Creator has not been initialized! Create an event to initialize!", event, client);
                    }
                } else if (function.equalsIgnoreCase("endDate") || function.equalsIgnoreCase("end")) {
                    if (EventCreator.getCreator().hasPreEvent(guildId)) {
                        String dateRaw = args[1].trim();
                        if (dateRaw.length() > 10) {
                            try {
                                //Do a lot of date shuffling to get to proper formats and shit like that.
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss");
                                TimeZone tz = TimeZone.getTimeZone(EventCreator.getCreator().getPreEvent(guildId).getTimeZone());
                                sdf.setTimeZone(tz);
                                Date dateObj = sdf.parse(dateRaw);
                                DateTime dateTime = new DateTime(dateObj);
                                EventDateTime eventDateTime = new EventDateTime();
                                eventDateTime.setDateTime(dateTime);

                                //Wait! Lets check now if its in the future and not the past!
                                if (!Validator.inPast(dateRaw, tz) && !Validator.endBeforeStart(dateRaw, tz, EventCreator.getCreator().getPreEvent(guildId))) {
                                    //Date shuffling done, now actually apply all that damn stuff here.
                                    EventCreator.getCreator().getPreEvent(guildId).setEndDateTime(eventDateTime);

                                    //Apply viewable date/times...
                                    SimpleDateFormat sdfV = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss");
                                    Date dateObjV = sdfV.parse(dateRaw);
                                    DateTime dateTimeV = new DateTime(dateObjV);
                                    EventDateTime eventDateTimeV = new EventDateTime();
                                    eventDateTimeV.setDateTime(dateTimeV);
                                    EventCreator.getCreator().getPreEvent(guildId).setViewableEndDate(eventDateTimeV);
                                    Message.sendMessage("Event end date (yyyy/MM/dd) set to: `" + EventMessageFormatter.getHumanReadableDate(eventDateTimeV) + "`"
                                            + Message.lineBreak
                                            + "Event end time (HH:mm) set to: `"
                                            + EventMessageFormatter.getHumanReadableTime(eventDateTimeV) + "`"
                                            + Message.lineBreak + Message.lineBreak
                                            + "If you would like a specific color for your event use `!event color <name OR id>` to list all colors use `!event color list`" + Message.lineBreak + "Otherwise use `!event review` to review the event!", event, client);
                                } else {
                                    //Oops! Time is in the past or before the starting time...
                                    Message.sendMessage("Sorry >.< but I can't schedule an event that is in the past or has an ending before the starting time!!! Please make sure you typed everything correctly.", event, client);
                                }
                            } catch (ParseException e) {
                                Message.sendMessage("Invalid Date & Time specified!", event, client);
                            }
                        } else {
                            Message.sendMessage("Invalid date/time format! Use `yyyy/MM/dd-HH:mm:ss`", event, client);
                        }
                    } else {
                        Message.sendMessage("Event Creator has not been initialized! Create an event to initialize!", event, client);
                    }
                } else if (function.equalsIgnoreCase("summary")) {
                    if (EventCreator.getCreator().hasPreEvent(guildId)) {
                        String content = getContent(args);
                        EventCreator.getCreator().getPreEvent(guildId).setSummary(content);
                        Message.sendMessage("Event summary set to: ```" + content + "```"
                                + Message.lineBreak + Message.lineBreak
                                + "Please specify the event description with `!event description <desc>`", event, client);
                    } else {
                        Message.sendMessage("Event Creator has not been initialized! Create an event to initialize!", event, client);
                    }
                } else if (function.equalsIgnoreCase("description")) {
                    if (EventCreator.getCreator().hasPreEvent(guildId)) {
                        String content = getContent(args);
                        EventCreator.getCreator().getPreEvent(guildId).setDescription(content);
                        Message.sendMessage("Event description set to: ```" + content + "```"
                                + Message.lineBreak + Message.lineBreak
                                + "Please specify the following: "
                                + Message.lineBreak
                                + "Start date & starting time(military) in `yyyy/MM/dd-HH:mm:ss` format with the command `!event start <DateAndTime>`", event, client);
                    } else {
                        Message.sendMessage("Event Creator has not been initialized! Create an event to initialize!", event, client);
                    }
                } else if (function.equalsIgnoreCase("color")) {
                    String value = args[1];
                    if (value.equalsIgnoreCase("list") || value.equalsIgnoreCase("colors")) {

                        StringBuilder list = new StringBuilder("All Colors: ");
                        for (EventColor ec : EventColor.values()) {
                            list.append(Message.lineBreak).append("Name: ").append(ec.name()).append(", ID: ").append(ec.getId());
                        }
                        list.append(Message.lineBreak).append(Message.lineBreak).append("Use `!event color <name OR ID>` to set an event's color!");

                        Message.sendMessage(list.toString().trim(), event, client);
                    } else {
                        if (EventCreator.getCreator().hasPreEvent(guildId)) {
                            //Attempt to get color.
                            if (EventColor.exists(value)) {
                                EventColor color = EventColor.fromNameOrHexOrID(value);
                                EventCreator.getCreator().getPreEvent(guildId).setColor(color);
                                Message.sendMessage("Event color set to: `" + color.name() + "`" + Message.lineBreak + Message.lineBreak + "Review the event with `!event review` to verify everything is correct and then confirm it with `!event confirm`", event, client);
                            } else {
                                Message.sendMessage("Invalid/Unsupported color! Use `!event color list` to view all supported colors!", event, client);
                            }
                        } else {
                            Message.sendMessage("Event Creator has not been initialized! Create an event to initialize!", event, client);
                        }
                    }
                } else {
                    Message.sendMessage("Invalid function!", event, client);
                }
            } else {
                String function = args[0];
                if (function.equalsIgnoreCase("create")) {
                    if (EventCreator.getCreator().hasPreEvent(guildId)) {
                        Message.sendMessage("Event Creator already started!", event, client);
                    } else {
                        if (!calendarData.getCalendarAddress().equalsIgnoreCase("primary")) {
                            EventCreator.getCreator().init(event);
                            Message.sendMessage("Event Creator initiated! Please specify event summary with `!event summary <summary>`", event, client);
                        } else {
                            Message.sendMessage("You cannot create an event when you do not have a calendar!", event, client);
                        }
                    }
                } else if (function.equalsIgnoreCase("copy")) {
                    if (EventCreator.getCreator().hasPreEvent(guildId)) {
                        Message.sendMessage("Event Creator already started!", event, client);
                    } else {
                        if (!calendarData.getCalendarAddress().equalsIgnoreCase("primary")) {
                            String eventId = args[1];
                            //Check if valid.
                            if (EventUtils.eventExists(guildId, eventId)) {
                                PreEvent preEvent = EventCreator.getCreator().init(event, eventId);
                                if (preEvent != null) {
                                    Message.sendMessage(EventMessageFormatter.getPreEventEmbed(preEvent), "Event Creator initialized! Event details copied! Please specify the date/times!", event, client);
                                } else {
                                    Message.sendMessage("Something went wrong! I'm sorry, try again!", event, client);
                                }
                            } else {
                                Message.sendMessage("I can't find that event! Are you sure the ID is correct?", event, client);
                            }
                        } else {
                            Message.sendMessage("You cannot cop an event when you do not have a calendar!", event, client);
                        }
                    }
                } else if (function.equalsIgnoreCase("summary")) {
                    if (EventCreator.getCreator().hasPreEvent(guildId)) {
                        String content = getContent(args);
                        EventCreator.getCreator().getPreEvent(guildId).setSummary(content);
                        Message.sendMessage("Event summary set to: ```" + content + "```"
                                + Message.lineBreak + Message.lineBreak
                                + "Please specify the event description with `!event description <desc>`", event, client);
                    } else {
                        Message.sendMessage("Event Creator has not been initialized! Create an event to initialize!", event, client);
                    }
                } else if (function.equalsIgnoreCase("description")) {
                    if (EventCreator.getCreator().hasPreEvent(guildId)) {
                        String content = getContent(args);
                        EventCreator.getCreator().getPreEvent(guildId).setDescription(content);
                        Message.sendMessage("Event description set to: '" + content + "'"
                                + Message.lineBreak + Message.lineBreak
                                + "Please specify the following: "
                                + Message.lineBreak
                                + "Starting date & starting time(military) in `yyyy/MM/dd-HH:mm:ss` format with the command `!event start <DateAndTime>`", event, client);
                    } else {
                        Message.sendMessage("Event Creator has not been initialized! Create an event to initialize!", event, client);
                    }
                } else {
                    Message.sendMessage("Invalid function!", event, client);
                }
            }
        } else {
            Message.sendMessage("You do not have sufficient permissions to use this DisCal command!", event, client);
        }
        return false;
    }

    /**
     * Gets the contents of the message at a set offset.
     * @param args The args of the command.
     * @return The contents of the message at a set offset.
     */
    private String getContent(String[] args) {
        StringBuilder content = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            content.append(args[i]).append(" ");
        }
        return content.toString().trim();
    }
}