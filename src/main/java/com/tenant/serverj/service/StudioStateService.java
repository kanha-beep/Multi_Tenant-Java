package com.tenant.serverj.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StudioStateService {
    private Map<String, Object> studioState = createInitialStudioState();

    public synchronized Map<String, Object> getStudioState() {
        return studioState;
    }

    public synchronized Map<String, Object> toggleFocusItem(String taskId) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> focus = (List<Map<String, Object>>) studioState.get("focus");
        List<Map<String, Object>> nextFocus = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> task : focus) {
            Map<String, Object> nextTask = new LinkedHashMap<String, Object>(task);
            if (taskId.equals(task.get("id"))) {
                nextTask.put("done", !(Boolean) task.get("done"));
            }
            nextFocus.add(nextTask);
        }
        studioState.put("focus", nextFocus);
        return studioState;
    }

    public synchronized Map<String, Object> captureStudioNote(String text) {
        String trimmedText = text == null ? "" : text.trim();
        if (trimmedText.isEmpty()) {
            return studioState;
        }

        String excerpt = trimmedText.length() > 220 ? trimmedText.substring(0, 217) + "..." : trimmedText;
        long now = System.currentTimeMillis();

        Map<String, Object> newNote = mapOf(
                "id", "note-" + now,
                "title", "Captured " + monthDayLabel(),
                "category", "Quick capture",
                "body", excerpt,
                "tags", Arrays.asList("captured", "inbox")
        );
        Map<String, Object> newActivity = mapOf(
                "id", "activity-" + now,
                "title", "Quick note captured",
                "description", excerpt,
                "time", Date.from(Instant.ofEpochMilli(now + 30L * 60L * 1000L)).toInstant().toString()
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> notes = new LinkedList<Map<String, Object>>((List<Map<String, Object>>) studioState.get("notes"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> activity = new LinkedList<Map<String, Object>>((List<Map<String, Object>>) studioState.get("activity"));
        notes.add(0, newNote);
        activity.add(0, newActivity);
        while (activity.size() > 4) {
            activity.remove(activity.size() - 1);
        }
        studioState.put("notes", notes);
        studioState.put("activity", activity);
        return studioState;
    }

    private Map<String, Object> createInitialStudioState() {
        return mapOf(
                "hero", mapOf(
                        "badge", "Focused system design",
                        "title", "Write, plan, and align in one quiet workspace.",
                        "description", "Northstar brings product plans, meeting notes, and weekly focus into a single surface with the calm visual rhythm people love in modern productivity tools."
                ),
                "summary", "A shared operating system for product, design, and operations. Fewer tabs, clearer decisions, and much less drift.",
                "metrics", Arrays.asList(
                        mapOf("label", "active projects", "value", "06"),
                        mapOf("label", "docs updated today", "value", "18"),
                        mapOf("label", "focus items complete", "value", "72%")
                ),
                "navigation", Arrays.asList("Executive overview", "Weekly roadmap", "Team docs", "Decision log", "Launch prep"),
                "focus", Arrays.asList(
                        mapOf("id", "focus-1", "title", "Lock homepage narrative", "done", true),
                        mapOf("id", "focus-2", "title", "Review onboarding copy", "done", false),
                        mapOf("id", "focus-3", "title", "Publish design tokens", "done", false),
                        mapOf("id", "focus-4", "title", "Confirm launch checklist", "done", true)
                ),
                "projects", Arrays.asList(
                        mapOf("id", "project-1", "name", "Product Narrative Refresh", "stage", "Writing", "percent", 84,
                                "description", "Clarify messaging across hero, pricing, and onboarding.",
                                "detail", "This workstream aligns the homepage, activation flow, and lifecycle emails so the whole product feels like one sentence instead of a pile of screens.",
                                "highlights", Arrays.asList(
                                        "Hero copy now leads with outcomes instead of feature labels.",
                                        "Launch checklist is mapped directly to the new IA.",
                                        "Marketing and product teams are reviewing one shared brief."
                                )),
                        mapOf("id", "project-2", "name", "Workspace Navigation", "stage", "Systems", "percent", 68,
                                "description", "Reduce sidebar noise and simplify pathfinding for new users.",
                                "detail", "The navigation pass trims dead-end pages, groups frequent actions, and gives every team a cleaner mental model for where work belongs.",
                                "highlights", Arrays.asList(
                                        "Primary destinations cut from eleven to five.",
                                        "Templates moved closer to page creation.",
                                        "Search and recent activity now support re-entry flows."
                                )),
                        mapOf("id", "project-3", "name", "Weekly Operating Rhythm", "stage", "Ops", "percent", 57,
                                "description", "Turn meetings, notes, and status updates into one weekly cadence.",
                                "detail", "Instead of separate tools for planning and reporting, the weekly rhythm centers on a shared brief, a focus list, and decision notes that stay visible after the meeting ends.",
                                "highlights", Arrays.asList(
                                        "Monday priorities sync directly into the focus queue.",
                                        "Meeting notes are tagged to project workstreams.",
                                        "Leads can scan progress without opening ten docs."
                                ))
                ),
                "notes", Arrays.asList(
                        mapOf("id", "note-1", "title", "Homepage Sprint Brief", "category", "Design",
                                "body", "Keep the layout clear and editorial. Each section should feel intentional, with steady spacing, strong type hierarchy, and obvious next actions. The interface must feel productive before it feels promotional.",
                                "tags", Arrays.asList("hero", "content", "launch")),
                        mapOf("id", "note-2", "title", "Decision Log Template", "category", "Operations",
                                "body", "Capture the context, the final call, the owner, and the follow-up date. The point is not more writing. It is making the next conversation shorter and sharper.",
                                "tags", Arrays.asList("template", "ops", "rituals")),
                        mapOf("id", "note-3", "title", "Customer Signal Roundup", "category", "Research",
                                "body", "Users are asking for calm defaults, faster orientation, and less clutter in the first session. The new workspace should make progress visible without screaming for attention.",
                                "tags", Arrays.asList("research", "ux", "insights"))
                ),
                "activity", Arrays.asList(
                        mapOf("id", "activity-1", "title", "Launch brief updated", "description", "Three release notes were merged into the main planning page.", "time", Date.from(Instant.now().plusSeconds(5L * 60L * 60L)).toInstant().toString()),
                        mapOf("id", "activity-2", "title", "Design review booked", "description", "Homepage polish review scheduled with product and brand.", "time", Date.from(Instant.now().plusSeconds(11L * 60L * 60L)).toInstant().toString()),
                        mapOf("id", "activity-3", "title", "Docs handoff complete", "description", "Editorial pass delivered to the onboarding workstream.", "time", Date.from(Instant.now().plusSeconds(26L * 60L * 60L)).toInstant().toString())
                )
        );
    }

    private String monthDayLabel() {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(ZoneOffset.UTC);
        String month = now.getMonth().getDisplayName(TextStyle.SHORT, Locale.US);
        return month + " " + now.getDayOfMonth();
    }

    private Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int index = 0; index < entries.length; index += 2) {
            map.put(entries[index].toString(), entries[index + 1]);
        }
        return map;
    }
}

