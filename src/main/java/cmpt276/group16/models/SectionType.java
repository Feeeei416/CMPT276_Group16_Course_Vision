package cmpt276.group16.models;

// The kind of class a section is, from sectionCode in the SFU Course Outlines API.
// The API docs say the values "include" LEC, TUT, LAB and SEM, and real data has more,
// for example "OPL" (open lab) in MATH 152. The importer saves any value it doesn't know
// as OTHER, so a new value from SFU never stops an import
public enum SectionType {
    LEC,
    TUT,
    LAB,
    SEM,
    OTHER
}
