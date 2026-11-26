// Course suggestions as the student types, for any box with data-course-search="<id of a JSON script>".
// The JSON is a list of { code, subject, number, title, units } that the server puts in the page
// (CourseOptions.json), so suggestions are instant and nothing is fetched per key.
// data-fill says what picking a course does:
//   "submit"  puts the code in the box and sends its form (My schedule's Add a course)
//   "form"    fills the subject, number, title and units fields of its form (My courses' Add and Edit course)
// Without JavaScript none of this runs, and the boxes work as plain text boxes
(function () {
    'use strict';

    var MAX_SUGGESTIONS = 8;

    function readCourses(id) {
        var script = document.getElementById(id);
        if (!script) {
            return [];
        }
        try {
            return JSON.parse(script.textContent);
        } catch (e) {
            return [];
        }
    }

    // "cmpt 20" -> "CMPT20", so "CMPT 201", "cmpt201" and "CMPT  201" all match the same way
    function compact(text) {
        return text.toUpperCase().replace(/\s+/g, '');
    }

    // "CMPT 20" -> { subject: "CMPT", digits: "20" }; null unless it's letters followed by a number
    function split(typed) {
        var parts = compact(typed).match(/^([A-Z]+)(\d[0-9A-Z]*)$/);
        return parts ? { subject: parts[1], digits: parts[2] } : null;
    }

    // In this order:
    //   1. the code starts with what was typed: CMPT 20 -> CMPT 201
    //   2. same subject, and the number has the typed digits inside it: CMPT 20 -> CMPT 120, CMPT 320
    //   3. the title contains it: systems -> Systems Programming
    function match(courses, typed) {
        var query = compact(typed);
        if (query.length === 0) {
            return [];
        }
        var words = typed.trim().toLowerCase();
        var code = split(typed);
        var byCode = [];
        var byNumber = [];
        var byTitle = [];
        courses.forEach(function (course) {
            if (compact(course.code).indexOf(query) === 0) {
                byCode.push(course);
            } else if (code && course.subject === code.subject && course.number.indexOf(code.digits) > 0) {
                byNumber.push(course);
            } else if (words.length >= 2 && course.title && course.title.toLowerCase().indexOf(words) >= 0) {
                byTitle.push(course);
            }
        });
        return byCode.concat(byNumber, byTitle).slice(0, MAX_SUGGESTIONS);
    }

    // Puts the typed part of the code in <mark>, ignoring the space: typing "CMPT20" marks "CMPT 20" in "CMPT 201"
    function markCode(code, typed) {
        var query = compact(typed);
        var out = document.createDocumentFragment();
        var marked = 0;
        var mark = document.createElement('mark');
        var rest = '';
        for (var i = 0; i < code.length; i++) {
            var ch = code.charAt(i);
            if (marked < query.length && (ch === ' ' || ch.toUpperCase() === query.charAt(marked))) {
                mark.textContent += ch;
                if (ch !== ' ') {
                    marked++;
                }
            } else {
                rest = code.substring(i);
                break;
            }
        }
        if (marked === query.length && marked > 0) {
            out.appendChild(mark);
            out.appendChild(document.createTextNode(rest));
            return out;
        }
        // Digits inside the number: typing "CMPT 20" marks "CMPT" and the "20" in "CMPT 120"
        var typedCode = split(typed);
        var space = code.indexOf(' ');
        var at = typedCode && space > 0 ? code.indexOf(typedCode.digits, space + 1) : -1;
        if (at > 0 && code.substring(0, space) === typedCode.subject) {
            var subjectMark = document.createElement('mark');
            subjectMark.textContent = code.substring(0, space);
            var digitsMark = document.createElement('mark');
            digitsMark.textContent = typedCode.digits;
            out.appendChild(subjectMark);
            out.appendChild(document.createTextNode(code.substring(space, at)));
            out.appendChild(digitsMark);
            out.appendChild(document.createTextNode(code.substring(at + typedCode.digits.length)));
            return out;
        }
        out.appendChild(document.createTextNode(code));
        return out;
    }

    // Puts the first place the typed words appear in the title in <mark>
    function markTitle(title, typed) {
        var out = document.createDocumentFragment();
        var words = typed.trim().toLowerCase();
        var at = words.length >= 2 ? title.toLowerCase().indexOf(words) : -1;
        if (at < 0) {
            out.appendChild(document.createTextNode(title));
            return out;
        }
        var mark = document.createElement('mark');
        mark.textContent = title.substring(at, at + words.length);
        out.appendChild(document.createTextNode(title.substring(0, at)));
        out.appendChild(mark);
        out.appendChild(document.createTextNode(title.substring(at + words.length)));
        return out;
    }

    function fieldIn(form, name) {
        return form ? form.querySelector('[name="' + name + '"]') : null;
    }

    function find(courses, subject, number) {
        var code = compact(subject) + compact(number);
        for (var i = 0; i < courses.length; i++) {
            if (compact(courses[i].code) === code) {
                return courses[i];
            }
        }
        return null;
    }

    // My courses: a gentle note when the units don't match what SFU lists now. Never blocks saving,
    // because units change between years (ENSC 204 was 1 unit in 2023 and 3 in 2024)
    function updateUnitsNote(form, courses) {
        var note = form.querySelector('[data-units-note]');
        if (!note) {
            return;
        }
        var subject = fieldIn(form, 'subject');
        var number = fieldIn(form, 'number');
        var units = fieldIn(form, 'units');
        var course = subject && number ? find(courses, subject.value, number.value) : null;
        if (course && units && units.value !== '' && Number(units.value) !== Number(course.units)) {
            note.textContent = 'SFU currently lists ' + course.code + ' as ' + course.units
                + (Number(course.units) === 1 ? ' unit' : ' units') + '. Check your transcript if yours is different.';
            note.hidden = false;
        } else {
            note.hidden = true;
        }
    }

    function fillForm(form, course, courses) {
        var fields = { subject: course.subject, number: course.number, title: course.title, units: course.units };
        Object.keys(fields).forEach(function (name) {
            var field = fieldIn(form, name);
            if (field) {
                field.value = fields[name];
            }
        });
        updateUnitsNote(form, courses);
    }

    // Typing the subject and number by hand fills in the title and units, but only when they're empty,
    // so nothing the student typed is ever replaced
    function watchCodeFields(form, courses) {
        var subject = fieldIn(form, 'subject');
        var number = fieldIn(form, 'number');
        var title = fieldIn(form, 'title');
        var units = fieldIn(form, 'units');
        if (!subject || !number) {
            return;
        }
        function fillBlanks() {
            var course = find(courses, subject.value, number.value);
            if (course) {
                if (title && title.value.trim() === '') {
                    title.value = course.title;
                }
                if (units && units.value === '') {
                    units.value = course.units;
                }
            }
            updateUnitsNote(form, courses);
        }
        subject.addEventListener('change', fillBlanks);
        number.addEventListener('change', fillBlanks);
        if (units) {
            units.addEventListener('input', function () { updateUnitsNote(form, courses); });
        }
        updateUnitsNote(form, courses);
    }

    function setUp(input, index) {
        var courses = readCourses(input.getAttribute('data-course-search'));
        var mode = input.getAttribute('data-fill');
        var form = input.form || input.closest('form');
        var list = document.createElement('ul');
        list.className = 'course-suggestions';
        list.id = 'course-suggestions-' + index;
        list.setAttribute('role', 'listbox');
        list.hidden = true;
        input.insertAdjacentElement('afterend', list);
        input.setAttribute('role', 'combobox');
        input.setAttribute('aria-autocomplete', 'list');
        input.setAttribute('aria-controls', list.id);
        input.setAttribute('aria-expanded', 'false');
        input.setAttribute('autocomplete', 'off');
        // The browser's own list would cover ours
        input.removeAttribute('list');

        var shown = [];
        var active = -1;

        function close() {
            list.hidden = true;
            input.setAttribute('aria-expanded', 'false');
            input.removeAttribute('aria-activedescendant');
            active = -1;
        }

        function highlight(i) {
            active = i;
            Array.prototype.forEach.call(list.children, function (item, j) {
                item.setAttribute('aria-selected', j === i ? 'true' : 'false');
            });
            if (i >= 0) {
                input.setAttribute('aria-activedescendant', list.children[i].id);
                list.children[i].scrollIntoView({ block: 'nearest' });
            }
        }

        function choose(course) {
            close();
            if (mode === 'submit') {
                input.value = course.code;
                if (form) {
                    form.submit();
                }
            } else {
                input.value = '';
                fillForm(form, course, courses);
                var title = fieldIn(form, 'title');
                if (title) {
                    title.focus();
                }
            }
        }

        function show() {
            shown = match(courses, input.value);
            list.textContent = '';
            if (shown.length === 0) {
                close();
                return;
            }
            shown.forEach(function (course, i) {
                var item = document.createElement('li');
                item.id = list.id + '-' + i;
                item.setAttribute('role', 'option');
                item.setAttribute('aria-selected', 'false');
                var code = document.createElement('span');
                code.className = 'course-suggestion-code';
                code.appendChild(markCode(course.code, input.value));
                var title = document.createElement('span');
                title.className = 'course-suggestion-title';
                title.appendChild(markTitle(course.title || '', input.value));
                item.appendChild(code);
                item.appendChild(title);
                // mousedown rather than click, so the box doesn't lose focus and close the list first
                item.addEventListener('mousedown', function (event) {
                    event.preventDefault();
                    choose(course);
                });
                list.appendChild(item);
            });
            list.hidden = false;
            input.setAttribute('aria-expanded', 'true');
            highlight(-1);
        }

        input.addEventListener('input', show);
        input.addEventListener('focus', function () {
            if (input.value.trim() !== '') {
                show();
            }
        });
        input.addEventListener('blur', close);
        input.addEventListener('keydown', function (event) {
            if (list.hidden) {
                return;
            }
            if (event.key === 'ArrowDown') {
                event.preventDefault();
                highlight(Math.min(active + 1, shown.length - 1));
            } else if (event.key === 'ArrowUp') {
                event.preventDefault();
                highlight(Math.max(active - 1, 0));
            } else if (event.key === 'Enter' && active >= 0) {
                event.preventDefault();
                choose(shown[active]);
            } else if (event.key === 'Escape') {
                close();
            }
        });

        if (mode === 'form' && form) {
            watchCodeFields(form, courses);
        }
        // Hidden until now, since without JavaScript it would do nothing
        var wrapper = input.closest('[data-needs-js]');
        if (wrapper) {
            wrapper.hidden = false;
        }
    }

    document.querySelectorAll('[data-course-search]').forEach(setUp);
})();
