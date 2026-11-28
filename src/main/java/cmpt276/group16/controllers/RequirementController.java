package cmpt276.group16.controllers;

import java.io.IOException;
import java.util.*;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import cmpt276.group16.graduation.RequirementEditor;
import cmpt276.group16.models.RequirementSet;
import jakarta.servlet.http.HttpServletResponse;

// The IT manager's Graduation requirements page: see a set's requirements, and add, change or remove one
// typed as JSON in the requirements.json format. RequirementEditor does the checking and saving.
// Only IT managers get here: AuthInterceptor sends every other role home from /it/
@Controller
public class RequirementController {

    private final RequirementEditor editor;

    public RequirementController(RequirementEditor editor) {
        this.editor = editor;
    }

    /** GET /it/requirements?set=5: the chosen set's requirements, the first set when none is chosen. */
    @GetMapping("/it/requirements")
    public String showRequirements(@RequestParam(required = false) Long set, Model model) {
        List<RequirementSet> sets = editor.sets();
        model.addAttribute("sets", sets);
        // An id that isn't a set (an old link, say) shows the first set instead of an error
        Optional<RequirementSet> chosen = sets.stream().filter(s -> s.getId().equals(set)).findFirst()
                .or(() -> sets.stream().findFirst());
        chosen.ifPresent(s -> {
            model.addAttribute("chosen", s);
            model.addAttribute("rows", editor.rows(s));
        });
        return "it/requirements";
    }

    /** GET /it/requirements/sets/{setId}/add : an empty box to type a new requirement in, with examples. */
    @GetMapping("/it/requirements/sets/{setId}/add")
    public String addRequirement(@PathVariable Long setId, Model model) {
        model.addAttribute("set", editor.setById(setId));
        // After a mistake, "json" is already there from the flash: what was typed
        if (!model.containsAttribute("json")) {
            model.addAttribute("json", "");
        }
        return "it/requirement-add";
    }

    /** POST /it/requirements/sets/{setId}/add : adds the typed requirement at the end of the set. */
    @PostMapping("/it/requirements/sets/{setId}/add")
    public String add(@PathVariable Long setId, @RequestParam(defaultValue = "") String json,
                      RedirectAttributes redirectAttributes) {
        try {
            editor.add(setId, json);
            redirectAttributes.addFlashAttribute("message", "Requirement added.");
            return "redirect:/it/requirements?set=" + setId;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            // Kept, so the mistake can be fixed instead of typed again
            redirectAttributes.addFlashAttribute("json", json);
            return "redirect:/it/requirements/sets/" + setId + "/add";
        }
    }

    /** GET /it/requirements/{id}/edit : the requirement's JSON in a box to change. */
    @GetMapping("/it/requirements/{id}/edit")
    public String editRequirement(@PathVariable Long id, Model model) {
        model.addAttribute("set", editor.setOf(id));
        model.addAttribute("requirementId", id);
        // After a mistake, "json" is already there from the flash: what was typed, not what's saved
        if (!model.containsAttribute("json")) {
            model.addAttribute("json", editor.json(id));
        }
        return "it/requirement-edit";
    }

    /** POST /it/requirements/{id}/edit: replaces the requirement with the one typed. */
    @PostMapping("/it/requirements/{id}/edit")
    public String update(@PathVariable Long id, @RequestParam(defaultValue = "") String json,
                         RedirectAttributes redirectAttributes) {
        // Looked up first: once the requirement is replaced, its id no longer leads to the set
        Long setId = editor.setOf(id).getId();
        try {
            editor.update(id, json);
            redirectAttributes.addFlashAttribute("message", "Requirement saved.");
            return "redirect:/it/requirements?set=" + setId;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("json", json);
            return "redirect:/it/requirements/" + id + "/edit";
        }
    }

    /** POST /it/requirements/{id}/remove: removes the requirement. */
    @PostMapping("/it/requirements/{id}/remove")
    public String remove(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        // Looked up first: once the requirement is removed, its id no longer leads to the set
        Long setId = editor.setOf(id).getId();
        editor.remove(id);
        redirectAttributes.addFlashAttribute("message", "Requirement removed.");
        return "redirect:/it/requirements?set=" + setId;
    }

    /** POST /it/requirements/sets/{setId}/copy: copies the set to a new entry year, then shows the copy. */
    @PostMapping("/it/requirements/sets/{setId}/copy")
    public String copy(@PathVariable Long setId, @RequestParam(required = false) Integer entryYear,
                       RedirectAttributes redirectAttributes) {
        if (entryYear == null) {
            redirectAttributes.addFlashAttribute("error", "Enter the entry year to copy to.");
            return "redirect:/it/requirements?set=" + setId;
        }
        try {
            RequirementSet copy = editor.copy(setId, entryYear);
            redirectAttributes.addFlashAttribute("message",
                    "Copied to " + copy.getShowName() + ". Change anything that's different for that year.");
            return "redirect:/it/requirements?set=" + copy.getId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/it/requirements?set=" + setId;
        }
    }

    // A requirement or set that doesn't exist, from an old link or a changed form
    @ExceptionHandler(NoSuchElementException.class)
    public void notFound(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
