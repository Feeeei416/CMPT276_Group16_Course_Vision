# Graduation requirements (#68)

The requirements Course Vision checks for each program and entry year, taken from the SFU Calendar. The Graduation Report (#69) compares a student's record and main plan against these.

Course Vision supports two programs and two entry years, so there are four requirement sets:

| Program | Entry year | Calendar used |
|---|---|---|
| Computing Science Major, **BSc** | 2023 | [Fall 2023](https://www.sfu.ca/students/calendar/2023/fall/programs/computing-science/major/bachelor-of-science-or-bachelor-of-arts.html) |
| Computing Science Major, **BSc** | 2024 | [Fall 2024](https://www.sfu.ca/students/calendar/2024/fall/programs/computing-science/major/bachelor-of-science-or-bachelor-of-arts.html) |
| Engineering Science, Computer Engineering Option, **BASc** | 2023 | [Fall 2023](https://www.sfu.ca/students/calendar/2023/fall/programs/engineering-science-computer-engineering-option/major/bachelor-of-applied-science.html) |
| Engineering Science, Computer Engineering Option, **BASc** | 2024 | [Fall 2024](https://www.sfu.ca/students/calendar/2024/fall/programs/engineering-science-computer-engineering-option/major/bachelor-of-applied-science.html) |

A student follows the Calendar of the year they entered, so each year is stored as its own set even where two years are the same.

## Kinds of requirement

| Kind | Meaning | Example |
|---|---|---|
| `COURSE` | This course must be completed | CMPT 225 |
| `CHOOSE` | N courses from a list | 1 of MATH 150, 151, 154, 157 |
| `UNITS_FROM` | N units from a list or a range of courses, with optional exclusions and a cap on lower-level courses | 12 units of CMPT 400+, not CMPT 415, 416, 498 |
| `AREAS` | N courses from N different areas, which must include certain courses | 5 courses from 5 of the 6 Table I areas, including CMPT 307 |
| `DESIGNATION` | N units of courses with a WQB designation | 6 units of W |
| `TOTAL_UNITS` | N units overall, or N upper-division units | 45 upper-division units |
| `NOTE` | A rule the app can't check from a transcript, shown as "check with an advisor" | Upper-division GPA of 2.00 |

## Rules used everywhere

- **Passing grade.** A course counts once it has a passing grade (D or better, P, or Transfer credit). The Calendar's C- rules are about prerequisites, which eligibility already checks. WQB courses need **C- or better**, as the Calendar says.
- **Co-op work terms** (CMPT 426–430, ENSC 194–396) never count toward units or electives, only toward the co-op requirement.
- **Counting a course once.** In Computing Science, Breadth, Depth and the BSc tables each say "additional", so one course counts toward only one of them. Required courses and WQB designations can overlap with anything.
- **Taken twice.** A course taken twice counts once, from its passing attempt.
- **Upper division** means courses numbered 300 and above.

---

## Computing Science, BSc, entry 2024

### Lower division requirements
- `COURSE` × 10: CMPT 105W, CMPT 120, CMPT 125, CMPT 201, CMPT 210, CMPT 225, CMPT 276, CMPT 295, MACM 101, STAT 271
- `CHOOSE` 1 (Calculus I): MATH 150, MATH 151, MATH 154, MATH 157
- `CHOOSE` 1 (Calculus II): MATH 152, MATH 155, MATH 158
- `CHOOSE` 1 (Linear algebra): MATH 232, MATH 240
- `NOTE`: MATH 154, 155, 157 and 158 need B+ or better and school permission.

### Upper division requirements
- `COURSE`: CMPT 376W
- `TOTAL_UNITS`: 45 upper-division units

### Breadth
- `AREAS`: 5 courses from 5 of the 6 Table I areas, including CMPT 307

| Table I area | Courses |
|---|---|
| Artificial Intelligence | CMPT 310, 340, 410, 411, 413, 417, 419, 420 |
| Visual and Interactive Computing | CMPT 361, 363, 365, 412, 461, 464, 466, 467, 469 |
| Computing Systems | CMPT 303, 305, 371, 379, 403, 431, 433, 450, 471, 479, 499 |
| Information Systems | CMPT 353, 354, 362, 372, 441, 454, 456, 459, 474 |
| Programming Languages and Software | CMPT 373, 383, 384, 473, 475, 477, 489 |
| Theoretical Computing Science | CMPT 307, 308, 404, 405, 406, 407, 409, 476, MACM 300 |

### Depth
- `UNITS_FROM`: 12 units of CMPT 400 and above, not CMPT 415, 416 or 498

### BSc
- `COURSE`: MACM 316
- `CHOOSE` 2 from Table I, Table II or Table III. Table II is empty. Table III is MACM 316, MACM 401, MACM 442, MATH 308, MATH 340, MATH 343. MACM 316 is left out of this choice, since it's already required and the two courses must be additional.

### WQB
- `DESIGNATION`: 6 units of W
- `DESIGNATION`: 6 units of Q
- `DESIGNATION`: 6 units of B-Soc
- `DESIGNATION`: 6 units of B-Hum
- `DESIGNATION`: 6 units of B-Sci

### Totals and notes
- `TOTAL_UNITS`: 120 units
- `NOTE`: An upper-division GPA of 2.00 in program courses, and a 2.40 CGPA to stay in the program.
- `NOTE`: At least half of the units, and two thirds of upper-division units, taken at SFU.
- `NOTE`: 6 more units of breadth outside the major.

## Computing Science, BSc, entry 2023

The same as 2024, except:
- **Lower division:** **no CMPT 201** (9 required courses, not 10).
- **Breadth:** must include **both CMPT 300 and CMPT 307**.
- **Table I, Computing Systems:** has **CMPT 300** where 2024 has CMPT 303.

---

## Computer Engineering, BASc, entry 2024

### Required courses
The Calendar numbers 37 core courses: these 35, plus the Calculus I and Business choices below.
- `COURSE` × 35:
  - CHEM 121; CMPT 225, 276, 300; ECON 103
  - ENSC 100W, 105W, 120, 151, 180, 204, 220, 225, 251, 252, 254, 280, 320, 324, 327, 350, 351, 380, 405W, 406, 429, 440
  - MACM 201, 316; MATH 152, 232, 251, 260; PHYS 120, 121
- `CHOOSE` 1 (Calculus I): MATH 151, MATH 150
- `CHOOSE` 1 (Business): ENSC 410, ENSC 411
- `CHOOSE` 2 (Constrained electives): ENSC 450, ENSC 452, ENSC 453

### Co-op
- `CHOOSE` 3 (Work terms): ENSC 195, 295, 395, 196, 296, 396
- `NOTE`: At least two of the three must be industry terms (ENSC 195, 295, 395). ENSC 194 doesn't count.

### Engineering Science and Design (ESD) electives
- `UNITS_FROM`: 12 units, at most two 300-level courses, from:
  - ENSC 386, 413, 428, 383, 427, and whichever of ENSC 450, 452, 453 wasn't used as a constrained elective
  - any ENSC 300 or 400 course except ENSC 412
  - CMPT 305, 307, 308, 310, 354, 361, 363, 370, 373, 375, 379, 383, 384, 404, 405, 407, 408, 409, 411, 412, 414, 417, 418, 419, 431, 441, 454, 456, 459, 461, 464, 466, 467, 469, 470, 473, 474, 475, 477
  - Not counted: the required ENSC 300 and 400 courses (320, 324, 327, 350, 351, 380, 405W, 406, 410, 411, 429, 440) and the co-op terms ENSC 395 and 396, which "any ENSC 300 or 400 course" would otherwise include
- `NOTE`: At least one of ENSC 386, 413, 428, 383, 427, or the unused constrained elective. This list is from [the School of Engineering Science](https://www.sfu.ca/fas/study/current-students/school-of-engineering-science/undergraduates/program-requirements.html) and took effect in Summer 2025.

### Science elective
- `CHOOSE` 1 from [the pre-approved list](https://www.sfu.ca/fas/study/current-students/school-of-engineering-science/undergraduates/program-requirements/preapproved-electives.html):
  - **BISC:** 100, 101, 102, 202, 204, 300, 302, 303, 304W, 305, 306, 313, 316, 317, 326, 329, 333, 337, 366, 404W, 405, 407, 410, 414, 419, 422, 430, 432, 434, 435, 445, 455, 457
  - **BPK:** 105, 110, 142, 180W, 201, 205, 207, 241, 301, 303, 305, 306, 325, 326, 336, 402, 407, 412, 415, 426, 430, 431, 481
  - **CHEM:** 180, 215, 230, 260, 281, 316, 332, 371, 372, 380, 432, 450, 455, 462, 465
  - **EASC:** 101, 201, 202, 204, 205
  - **GEOG:** 111, 213, 214, 311, 313, 314, 317, 411
  - **GERO:** 407
  - **HSCI:** 100
  - **MASC:** 410, 440, 445, 446
  - **MBB:** 222, 308, 321, 322, 331, 421, 422, 423
  - **NUSC:** 342
  - **PHYS:** 211, 221, 255, 285, 332W, 344, 346, 347, 365, 384, 385, 390, 413, 415, 421, 431, 445, 455, 465, 485
- `NOTE`: CHEM 122 with 126, and CHEM 282 with 286, count together as one elective.

### Complementary studies and WQB
- `DESIGNATION`: 6 units of W (ENSC 105W and ENSC 405W cover it)
- `DESIGNATION`: 6 units of Q (the program's math courses cover it)
- `DESIGNATION`: 3 units of B-Hum
- `DESIGNATION`: 3 units of B-Soc (ECON 103 covers it)
- `DESIGNATION`: 9 units of B-Hum or B-Soc in total (three courses)
- `NOTE`: One complementary course must be from the CEAB "Central Issue, Methodology and Thought Process" list.

### Notes
- `NOTE`: A 2.40 CGPA for 300- and 400-level ENSC courses.
- `NOTE`: The Calendar doesn't state a total unit count. The report shows the units of the requirements above.

## Computer Engineering, BASc, entry 2023

The same as 2024, except:
- **ENSC 204** is **1 unit** (3 in 2024).
- **ESD electives:** the 2023 Calendar refers to the same School list. The Summer 2025 list above is the only one published, so 2023 students are checked against it too.

---

## Simplifications

1. **BSc only** for Computing Science. The BA and BEd credentials aren't supported.
2. **Areas of concentration** (optional extra credentials) aren't checked.
3. **GPA, residency, permission and "taken at SFU" rules** are shown as notes, not computed.
4. **Credit exclusions** (for example MATH 150 and 151 both taken) aren't checked. Course data doesn't record them.
5. **The CEAB complementary category** is a note, because course data has no flag for it.
6. **ESD electives for 2023** use the Summer 2025 list, the only one published.
