// UniversityConsoleApp.java
// Single-file console application — error-free

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UniversityConsoleApp (single file)
 * - Manage Students, Courses, Enrollments
 * - Persist to a binary file (serialization)
 * - Import/Export CSV for Students & Courses
 * - Undo/Redo using Command pattern
 * - Menu-driven console UI
 *
 * Compile: javac UniversityConsoleApp.java
 * Run:     java UniversityConsoleApp
 */
public class UniversityConsoleApp {
    public static void main(String[] args) {
        ConsoleUI ui = new ConsoleUI();
        ui.start();
    }
}

// ===== MODELS =====
class Student implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int id;
    private String name;
    private LocalDate dob;
    private String email;
    private String phone;

    public Student(int id, String name, LocalDate dob, String email, String phone) {
        this.id = id;
        this.name = name;
        this.dob = dob;
        this.email = email;
        this.phone = phone;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public LocalDate getDob() { return dob; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }

    public void setName(String name) { this.name = name; }
    public void setDob(LocalDate dob) { this.dob = dob; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }

    public String toString() {
        return String.format("#%d | %s | DOB: %s | email: %s | phone: %s",
                id,
                name,
                dob == null ? "-" : dob.toString(),
                email == null ? "-" : email,
                phone == null ? "-" : phone);
    }
}

class Course implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int id;
    private String code;
    private String title;
    private int credits;

    public Course(int id, String code, String title, int credits) {
        this.id = id;
        this.code = code;
        this.title = title;
        this.credits = credits;
    }

    public int getId() { return id; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public int getCredits() { return credits; }

    public void setCode(String code) { this.code = code; }
    public void setTitle(String title) { this.title = title; }
    public void setCredits(int credits) { this.credits = credits; }


    public String toString() {
        return String.format("#%d | %s | %s | %d credits", id, code, title, credits);
    }
}

class Enrollment implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int id;
    private final int studentId;
    private final int courseId;
    private Grade grade;
    private final LocalDate enrolledOn;

    public Enrollment(int id, int studentId, int courseId, LocalDate enrolledOn) {
        this.id = id;
        this.studentId = studentId;
        this.courseId = courseId;
        this.enrolledOn = enrolledOn;
    }

    public int getId() { return id; }
    public int getStudentId() { return studentId; }
    public int getCourseId() { return courseId; }
    public Grade getGrade() { return grade; }
    public LocalDate getEnrolledOn() { return enrolledOn; }

    public void setGrade(Grade grade) { this.grade = grade; }


    public String toString() {
        return String.format("Enroll#%d | student=%d | course=%d | on=%s | grade=%s",
                id, studentId, courseId, enrolledOn.toString(), grade == null ? "-" : grade.name());
    }
}

enum Grade {
    A(10), A_MINUS(9), B_PLUS(8), B(7), C(6), D(5), E(4), F(0);
    final int points;
    Grade(int p) { this.points = p; }
}

// ===== STORAGE =====
class DataStore implements Serializable {
    private static final long serialVersionUID = 1L;
    List<Student> students = new ArrayList<>();
    List<Course> courses = new ArrayList<>();
    List<Enrollment> enrollments = new ArrayList<>();
    AtomicInteger studentSeq = new AtomicInteger(1000);
    AtomicInteger courseSeq = new AtomicInteger(2000);
    AtomicInteger enrollmentSeq = new AtomicInteger(3000);

    public int nextStudentId() { return studentSeq.incrementAndGet(); }
    public int nextCourseId() { return courseSeq.incrementAndGet(); }
    public int nextEnrollmentId() { return enrollmentSeq.incrementAndGet(); }
}

class Storage {
    private static final String DEFAULT_FILE = "university.dat";

    public static void save(DataStore store) throws IOException {
        save(store, DEFAULT_FILE);
    }

    public static void save(DataStore store, String file) throws IOException {
        try (ObjectOutputStream o = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(Path.of(file))))) {
            o.writeObject(store);
        }
    }

    public static DataStore loadOrNew() {
        return loadOrNew(DEFAULT_FILE);
    }

    public static DataStore loadOrNew(String file) {
        if (!Files.exists(Path.of(file))) return new DataStore();
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(Path.of(file))))) {
            Object obj = in.readObject();
            if (obj instanceof DataStore) return (DataStore) obj;
        } catch (Exception e) {
            System.err.println("[WARN] Failed to load data: " + e.getMessage());
        }
        return new DataStore();
    }
}

// ===== CSV Utilities =====
class Csv {
    public static void exportStudents(List<Student> list, String file) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(Path.of(file))) {
            bw.write("id,name,dob,email,phone\n");
            for (Student s : list) {
                bw.write(String.join(",",
                        String.valueOf(s.getId()),
                        escape(s.getName()),
                        s.getDob() == null ? "" : s.getDob().toString(),
                        escape(s.getEmail() == null ? "" : s.getEmail()),
                        escape(s.getPhone() == null ? "" : s.getPhone())
                ));
                bw.write("\n");
            }
        }
    }

    public static void exportCourses(List<Course> list, String file) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(Path.of(file))) {
            bw.write("id,code,title,credits\n");
            for (Course c : list) {
                bw.write(String.join(",",
                        String.valueOf(c.getId()),
                        escape(c.getCode()),
                        escape(c.getTitle()),
                        String.valueOf(c.getCredits())
                ));
                bw.write("\n");
            }
        }
    }

    public static List<Student> importStudents(String file, AtomicInteger seq) throws IOException {
        List<Student> result = new ArrayList<>();
        List<String> lines = Files.readAllLines(Path.of(file));
        boolean header = true;
        for (String line : lines) {
            if (header) { header = false; continue; }
            if (line.isBlank()) continue;
            String[] parts = split(line);
            if (parts.length < 5) continue;
            int id = tryParseInt(parts[0], seq.incrementAndGet());
            String name = unescape(parts[1]);
            LocalDate dob = parts[2].isBlank() ? null : LocalDate.parse(parts[2]);
            String email = unescape(parts[3]);
            String phone = parts[4].isBlank() ? null : unescape(parts[4]);
            result.add(new Student(id, name, dob, email, phone));
        }
        int max = result.stream().mapToInt(Student::getId).max().orElse(seq.get());
        seq.set(Math.max(seq.get(), max));
        return result;
    }

    public static List<Course> importCourses(String file, AtomicInteger seq) throws IOException {
        List<Course> result = new ArrayList<>();
        List<String> lines = Files.readAllLines(Path.of(file));
        boolean header = true;
        for (String line : lines) {
            if (header) { header = false; continue; }
            if (line.isBlank()) continue;
            String[] parts = split(line);
            if (parts.length < 4) continue;
            int id = tryParseInt(parts[0], seq.incrementAndGet());
            String code = unescape(parts[1]);
            String title = unescape(parts[2]);
            int credits = tryParseInt(parts[3], 3);
            result.add(new Course(id, code, title, credits));
        }
        int max = result.stream().mapToInt(Course::getId).max().orElse(seq.get());
        seq.set(Math.max(seq.get(), max));
        return result;
    }

    private static String escape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String unescape(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).replace("\"\"", "\"");
        }
        return s;
    }

    private static String[] split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQ) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else inQ = false;
                } else cur.append(ch);
            } else {
                if (ch == '"') inQ = true;
                else if (ch == ',') { out.add(cur.toString()); cur.setLength(0); }
                else cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out.toArray(String[]::new);
    }

    private static int tryParseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
}

// ===== COMMANDS (Undo/Redo) =====
interface Command { void execute(); void undo(); String label(); }

class UndoManager {
    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();

    public void doIt(Command c) {
        c.execute();
        undoStack.push(c);
        redoStack.clear();
    }

    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        Command c = undoStack.pop();
        c.undo();
        redoStack.push(c);
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        Command c = redoStack.pop();
        c.execute();
        undoStack.push(c);
        return true;
    }

    public List<String> history() {
        List<String> list = new ArrayList<>();
        for (Command c : undoStack) list.add(c.label());
        return list;
    }
}

// Concrete commands for Student/Course/Enrollment and bulk imports
class AddStudentCmd implements Command {
    private final DataStore ds; private final Student s;
    public AddStudentCmd(DataStore ds, Student s) { this.ds = ds; this.s = s; }
    public void execute() { ds.students.add(s); }
    public void undo() { ds.students.removeIf(x -> x.getId() == s.getId()); }
    public String label() { return "AddStudent(" + s.getId() + ")"; }
}

class UpdateStudentCmd implements Command {
    private final DataStore ds; private final Student before, after;
    public UpdateStudentCmd(DataStore ds, Student before, Student after) { this.ds = ds; this.before = before; this.after = after; }
    public void execute() {
        for (ListIterator<Student> it = ds.students.listIterator(); it.hasNext();) {
            Student cur = it.next();
            if (cur.getId() == after.getId()) { it.set(after); break; }
        }
    }
    public void undo() {
        for (ListIterator<Student> it = ds.students.listIterator(); it.hasNext();) {
            Student cur = it.next();
            if (cur.getId() == before.getId()) { it.set(before); break; }
        }
    }
    public String label() { return "UpdateStudent(" + after.getId() + ")"; }
}

class RemoveStudentCmd implements Command {
    private final DataStore ds; private final Student s; private final List<Enrollment> removed;
    public RemoveStudentCmd(DataStore ds, Student s, List<Enrollment> removed) { this.ds = ds; this.s = s; this.removed = new ArrayList<>(removed); }
    public void execute() { ds.students.removeIf(x -> x.getId() == s.getId()); ds.enrollments.removeIf(e -> e.getStudentId() == s.getId()); }
    public void undo() { ds.students.add(s); ds.enrollments.addAll(removed); }
    public String label() { return "RemoveStudent(" + s.getId() + ")"; }
}

class BulkAddStudentsCmd implements Command {
    private final DataStore ds; private final List<Student> list;
    public BulkAddStudentsCmd(DataStore ds, List<Student> list) { this.ds = ds; this.list = new ArrayList<>(list); }
    public void execute() { ds.students.addAll(list); }
    public void undo() { ds.students.removeAll(list); }
    public String label() { return "BulkAddStudents(" + list.size() + ")"; }
}

class AddCourseCmd implements Command {
    private final DataStore ds; private final Course c;
    public AddCourseCmd(DataStore ds, Course c) { this.ds = ds; this.c = c; }
    public void execute() { ds.courses.add(c); }
    public void undo() { ds.courses.removeIf(x -> x.getId() == c.getId()); }
    public String label() { return "AddCourse(" + c.getId() + ")"; }
}

class UpdateCourseCmd implements Command {
    private final DataStore ds; private final Course before, after;
    public UpdateCourseCmd(DataStore ds, Course before, Course after) { this.ds = ds; this.before = before; this.after = after; }
    public void execute() {
        for (ListIterator<Course> it = ds.courses.listIterator(); it.hasNext();) {
            Course cur = it.next();
            if (cur.getId() == after.getId()) { it.set(after); break; }
        }
    }
    public void undo() {
        for (ListIterator<Course> it = ds.courses.listIterator(); it.hasNext();) {
            Course cur = it.next();
            if (cur.getId() == before.getId()) { it.set(before); break; }
        }
    }
    public String label() { return "UpdateCourse(" + after.getId() + ")"; }
}

class RemoveCourseCmd implements Command {
    private final DataStore ds; private final Course c; private final List<Enrollment> removed;
    public RemoveCourseCmd(DataStore ds, Course c, List<Enrollment> removed) { this.ds = ds; this.c = c; this.removed = new ArrayList<>(removed); }
    public void execute() { ds.courses.removeIf(x -> x.getId() == c.getId()); ds.enrollments.removeIf(e -> e.getCourseId() == c.getId()); }
    public void undo() { ds.courses.add(c); ds.enrollments.addAll(removed); }
    public String label() { return "RemoveCourse(" + c.getId() + ")"; }
}

class BulkAddCoursesCmd implements Command {
    private final DataStore ds; private final List<Course> list;
    public BulkAddCoursesCmd(DataStore ds, List<Course> list) { this.ds = ds; this.list = new ArrayList<>(list); }
    public void execute() { ds.courses.addAll(list); }
    public void undo() { ds.courses.removeAll(list); }
    public String label() { return "BulkAddCourses(" + list.size() + ")"; }
}

class AddEnrollmentCmd implements Command {
    private final DataStore ds; private final Enrollment e;
    public AddEnrollmentCmd(DataStore ds, Enrollment e) { this.ds = ds; this.e = e; }
    public void execute() { ds.enrollments.add(e); }
    public void undo() { ds.enrollments.removeIf(x -> x.getId() == e.getId()); }
    public String label() { return "AddEnrollment(" + e.getId() + ")"; }
}

class UpdateEnrollmentCmd implements Command {
    private final DataStore ds; private final Enrollment before, after;
    public UpdateEnrollmentCmd(DataStore ds, Enrollment before, Enrollment after) { this.ds = ds; this.before = before; this.after = after; }
    public void execute() {
        for (ListIterator<Enrollment> it = ds.enrollments.listIterator(); it.hasNext();) {
            Enrollment cur = it.next();
            if (cur.getId() == after.getId()) { it.set(after); break; }
        }
    }
    public void undo() {
        for (ListIterator<Enrollment> it = ds.enrollments.listIterator(); it.hasNext();) {
            Enrollment cur = it.next();
            if (cur.getId() == before.getId()) { it.set(before); break; }
        }
    }
    public String label() { return "UpdateEnrollment(" + after.getId() + ")"; }
}

class RemoveEnrollmentCmd implements Command {
    private final DataStore ds; private final Enrollment e;
    public RemoveEnrollmentCmd(DataStore ds, Enrollment e) { this.ds = ds; this.e = e; }
    public void execute() { ds.enrollments.removeIf(x -> x.getId() == e.getId()); }
    public void undo() { ds.enrollments.add(e); }
    public String label() { return "RemoveEnrollment(" + e.getId() + ")"; }
}

// ===== SERVICE LAYER =====
class UniversityService {
    final DataStore store;
    final UndoManager undo;

    public UniversityService(DataStore store, UndoManager undo) {
        this.store = store;
        this.undo = undo;
    }

    private void doCmd(Command c) { undo.doIt(c); }

    // Students
    public Student addStudent(String name, LocalDate dob, String email, String phone) {
        int id = store.nextStudentId();
        Student s = new Student(id, name, dob, email, phone);
        doCmd(new AddStudentCmd(store, s));
        return s;
    }

    public Optional<Student> getStudent(int id) {
        return store.students.stream().filter(s -> s.getId() == id).findFirst();
    }

    public List<Student> listStudents() { return new ArrayList<>(store.students); }

    public boolean updateStudent(int id, String name, LocalDate dob, String email, String phone) {
        Optional<Student> opt = getStudent(id);
        if (opt.isEmpty()) return false;
        Student before = copy(opt.get());
        if (name != null) opt.get().setName(name);
        if (dob != null) opt.get().setDob(dob);
        if (email != null) opt.get().setEmail(email);
        if (phone != null) opt.get().setPhone(phone);
        Student after = copy(opt.get());
        doCmd(new UpdateStudentCmd(store, before, after));
        return true;
    }

    public boolean removeStudent(int id) {
        Optional<Student> opt = getStudent(id);
        if (opt.isEmpty()) return false;
        List<Enrollment> removed = new ArrayList<>();
        store.enrollments.removeIf(e -> {
            if (e.getStudentId() == id) { removed.add(e); return true; }
            return false;
        });
        doCmd(new RemoveStudentCmd(store, opt.get(), removed));
        return store.students.removeIf(s -> s.getId() == id);
    }

    // Courses
    public Course addCourse(String code, String title, int credits) {
        int id = store.nextCourseId();
        Course c = new Course(id, code, title, credits);
        doCmd(new AddCourseCmd(store, c));
        return c;
    }

    public Optional<Course> getCourse(int id) {
        return store.courses.stream().filter(c -> c.getId() == id).findFirst();
    }

    public List<Course> listCourses() { return new ArrayList<>(store.courses); }

    public boolean updateCourse(int id, String code, String title, Integer credits) {
        Optional<Course> opt = getCourse(id);
        if (opt.isEmpty()) return false;
        Course before = copy(opt.get());
        if (code != null) opt.get().setCode(code);
        if (title != null) opt.get().setTitle(title);
        if (credits != null) opt.get().setCredits(credits);
        Course after = copy(opt.get());
        doCmd(new UpdateCourseCmd(store, before, after));
        return true;
    }

    public boolean removeCourse(int id) {
        Optional<Course> opt = getCourse(id);
        if (opt.isEmpty()) return false;
        List<Enrollment> removed = new ArrayList<>();
        store.enrollments.removeIf(e -> {
            if (e.getCourseId() == id) { removed.add(e); return true; }
            return false;
        });
        doCmd(new RemoveCourseCmd(store, opt.get(), removed));
        return store.courses.removeIf(c -> c.getId() == id);
    }

    // Enrollments
    public Enrollment enroll(int studentId, int courseId) {
        if (getStudent(studentId).isEmpty()) throw new IllegalArgumentException("Student not found");
        if (getCourse(courseId).isEmpty()) throw new IllegalArgumentException("Course not found");
        store.enrollments.stream()
                .filter(e -> e.getStudentId() == studentId && e.getCourseId() == courseId)
                .findAny().ifPresent(e -> { throw new IllegalStateException("Already enrolled"); });
        Enrollment e = new Enrollment(store.nextEnrollmentId(), studentId, courseId, LocalDate.now());
        doCmd(new AddEnrollmentCmd(store, e));
        return e;
    }

    public boolean assignGrade(int enrollmentId, Grade grade) {
        Optional<Enrollment> opt = store.enrollments.stream().filter(e -> e.getId() == enrollmentId).findFirst();
        if (opt.isEmpty()) return false;
        Enrollment before = copy(opt.get());
        opt.get().setGrade(grade);
        Enrollment after = copy(opt.get());
        doCmd(new UpdateEnrollmentCmd(store, before, after));
        return true;
    }

    public boolean dropEnrollment(int enrollmentId) {
        Optional<Enrollment> opt = store.enrollments.stream().filter(e -> e.getId() == enrollmentId).findFirst();
        if (opt.isEmpty()) return false;
        doCmd(new RemoveEnrollmentCmd(store, opt.get()));
        return store.enrollments.removeIf(e -> e.getId() == enrollmentId);
    }

    // Queries
    public List<Enrollment> listEnrollmentsForStudent(int studentId) {
        List<Enrollment> out = new ArrayList<>();
        for (Enrollment e : store.enrollments) if (e.getStudentId() == studentId) out.add(e);
        return out;
    }

    public List<Enrollment> listEnrollmentsForCourse(int courseId) {
        List<Enrollment> out = new ArrayList<>();
        for (Enrollment e : store.enrollments) if (e.getCourseId() == courseId) out.add(e);
        return out;
    }

    public double computeGpa(int studentId) {
        List<Enrollment> ens = listEnrollmentsForStudent(studentId);
        double points = 0, credits = 0;
        for (Enrollment e : ens) {
            if (e.getGrade() != null) {
                int cr = getCourse(e.getCourseId()).map(Course::getCredits).orElse(0);
                points += e.getGrade().points * cr;
                credits += cr;
            }
        }
        return credits == 0 ? 0.0 : points / credits;
    }

    public Map<Course, Integer> coursePopularity() {
        Map<Integer, Integer> cnt = new HashMap<>();
        for (Enrollment e : store.enrollments) cnt.merge(e.getCourseId(), 1, Integer::sum);
        Map<Course, Integer> out = new LinkedHashMap<>();
        store.courses.stream()
                .sorted(Comparator.comparingInt(c -> -cnt.getOrDefault(c.getId(), 0)))
                .forEach(c -> out.put(c, cnt.getOrDefault(c.getId(), 0)));
        return out;
    }

    public List<StudentGpa> topStudents(int n) {
        List<StudentGpa> list = new ArrayList<>();
        for (Student s : store.students) list.add(new StudentGpa(s, computeGpa(s.getId())));
        list.sort(Comparator.comparingDouble((StudentGpa sg) -> sg.gpa).reversed());
        return list.subList(0, Math.min(n, list.size()));
    }

    // Persistence & CSV
    public void save() throws IOException { Storage.save(store); }
    public void saveAs(String file) throws IOException { Storage.save(store, file); }

    public void importStudentsCsv(String file) throws IOException {
        List<Student> imported = Csv.importStudents(file, store.studentSeq);
        doCmd(new BulkAddStudentsCmd(store, imported));
    }
    public void importCoursesCsv(String file) throws IOException {
        List<Course> imported = Csv.importCourses(file, store.courseSeq);
        doCmd(new BulkAddCoursesCmd(store, imported));
    }
    public void exportStudentsCsv(String file) throws IOException { Csv.exportStudents(store.students, file); }
    public void exportCoursesCsv(String file) throws IOException { Csv.exportCourses(store.courses, file); }

    // Undo/Redo
    public boolean undo() { return undo.undo(); }
    public boolean redo() { return undo.redo(); }

    // Helpers
    private static Student copy(Student s) { return new Student(s.getId(), s.getName(), s.getDob(), s.getEmail(), s.getPhone()); }
    private static Course copy(Course c) { return new Course(c.getId(), c.getCode(), c.getTitle(), c.getCredits()); }
    private static Enrollment copy(Enrollment e) { Enrollment x = new Enrollment(e.getId(), e.getStudentId(), e.getCourseId(), e.getEnrolledOn()); x.setGrade(e.getGrade()); return x; }

    // Seed demo data
    public void seedDemo() {
        if (!store.students.isEmpty() || !store.courses.isEmpty()) return;
        Student s1 = addStudent("Aman Gupta", LocalDate.of(2004, 3, 12), "aman@example.com", "9876543210");
        Student s2 = addStudent("Riya Sharma", LocalDate.of(2003, 11, 2), "riya@example.com", null);
        Student s3 = addStudent("Karan Mehta", LocalDate.of(2005, 6, 25), "karan@example.com", "9811112222");
        Course c1 = addCourse("CS101", "Intro to Programming", 4);
        Course c2 = addCourse("MA102", "Discrete Mathematics", 3);
        Course c3 = addCourse("DB201", "Databases", 4);
        Enrollment e1 = enroll(s1.getId(), c1.getId()); assignGrade(e1.getId(), Grade.A);
        Enrollment e2 = enroll(s1.getId(), c2.getId()); assignGrade(e2.getId(), Grade.B_PLUS);
        Enrollment e3 = enroll(s2.getId(), c1.getId()); assignGrade(e3.getId(), Grade.A_MINUS);
        Enrollment e4 = enroll(s2.getId(), c3.getId()); assignGrade(e4.getId(), Grade.B);
        enroll(s3.getId(), c3.getId());
    }
}

class StudentGpa {
    public final Student student;
    public final double gpa;
    public StudentGpa(Student s, double g) { this.student = s; this.gpa = g; }
}

// ====== CONSOLE UI ======
class ConsoleUI {
    private final Scanner in = new Scanner(System.in);
    private final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private DataStore store;
    private UniversityService svc;
    private UndoManager um;

    public void start() {
        System.out.println("===== University Console App =====");
        store = Storage.loadOrNew();
        um = new UndoManager();
        svc = new UniversityService(store, um);
        if (store.students.isEmpty() && store.courses.isEmpty() && store.enrollments.isEmpty()) {
            System.out.println("(No data found. Seeding demo records...)");
            svc.seedDemo();
        }
        mainLoop();
    }

    private void mainLoop() {
        while (true) {
            printMainMenu();
            int choice = readInt("Choose option", 0, 12);
            try {
                switch (choice) {
                    case 1 -> studentMenu();
                    case 2 -> courseMenu();
                    case 3 -> enrollmentMenu();
                    case 4 -> analyticsMenu();
                    case 5 -> importExportMenu();
                    case 6 -> saveNow();
                    case 7 -> undo();
                    case 8 -> redo();
                    case 9 -> listAllData();
                    case 10 -> backupTo("backup_" + System.currentTimeMillis() + ".dat");
                    case 11 -> restoreFromPrompt();
                    case 12 -> help();
                    case 0 -> { exit(); return; }
                    default -> System.out.println("Unknown option");
                }
            } catch (Exception ex) {
                System.out.println("[ERROR] " + ex.getMessage());
            }
        }
    }

    private void printMainMenu() {
        System.out.println("\n---- Main Menu ----");
        System.out.println(" 1) Students");
        System.out.println(" 2) Courses");
        System.out.println(" 3) Enrollments & Grades");
        System.out.println(" 4) Analytics & Reports");
        System.out.println(" 5) Import/Export CSV");
        System.out.println(" 6) Save");
        System.out.println(" 7) Undo");
        System.out.println(" 8) Redo");
        System.out.println(" 9) List All Data");
        System.out.println("10) Backup (save-as)");
        System.out.println("11) Restore from file");
        System.out.println("12) Help");
        System.out.println(" 0) Exit");
    }

    // ----- STUDENT MENU -----
    private void studentMenu() {
        while (true) {
            System.out.println("\n-- Students --");
            System.out.println(" 1) Add student");
            System.out.println(" 2) Update student");
            System.out.println(" 3) Remove student");
            System.out.println(" 4) List students");
            System.out.println(" 5) Search (by name/email)");
            System.out.println(" 6) Sort (name / dob)");
            System.out.println(" 0) Back");
            int ch = readInt("Choose", 0, 6);
            if (ch == 0) return;
            switch (ch) {
                case 1 -> doAddStudent();
                case 2 -> doUpdateStudent();
                case 3 -> doRemoveStudent();
                case 4 -> listStudents();
                case 5 -> searchStudents();
                case 6 -> sortStudents();
                default -> System.out.println("Unknown option");
            }
        }
    }

    private void doAddStudent() {
        System.out.println("\nAdd Student:");
        String name = readNonEmpty("Full name");
        LocalDate dob = readDate("DOB (yyyy-MM-dd)");
        String email = readOptional("Email (optional)");
        String phone = readOptional("Phone (optional)");
        Student s = svc.addStudent(name, dob, email, phone);
        System.out.println("Added: " + s);
    }

    private void doUpdateStudent() {
        int id = readInt("Student ID", 1, Integer.MAX_VALUE);
        Optional<Student> opt = svc.getStudent(id);
        if (opt.isEmpty()) { System.out.println("Not found"); return; }
        System.out.println("Editing: " + opt.get());
        String name = readOptional("New name (leave empty to keep)");
        LocalDate dob = readOptionalDate("New DOB (yyyy-MM-dd, empty=keep)");
        String email = readOptional("New email (empty=keep)");
        String phone = readOptional("New phone (empty=keep)");
        boolean ok = svc.updateStudent(id, emptyToNull(name), dob, emptyToNull(email), emptyToNull(phone));
        System.out.println(ok ? "Updated." : "Failed.");
    }

    private void doRemoveStudent() {
        int id = readInt("Student ID", 1, Integer.MAX_VALUE);
        boolean ok = svc.removeStudent(id);
        System.out.println(ok ? "Removed." : "Not found.");
    }

    private void listStudents() {
        System.out.println("\nStudents:");
        for (Student s : svc.listStudents()) System.out.println(" - " + s);
    }

    private void searchStudents() {
        String key = readNonEmpty("Search text").toLowerCase();
        svc.listStudents().stream()
                .filter(s -> s.getName().toLowerCase().contains(key) || (s.getEmail() != null && s.getEmail().toLowerCase().contains(key)))
                .forEach(s -> System.out.println(" - " + s));
    }

    private void sortStudents() {
        System.out.println("Sort by: 1) name  2) dob");
        int k = readInt("Choose", 1, 2);
        List<Student> list = svc.listStudents();
        if (k == 1) list.sort(Comparator.comparing(Student::getName));
        else list.sort(Comparator.comparing(Student::getDob, Comparator.nullsLast(Comparator.naturalOrder())));
        list.forEach(s -> System.out.println(" - " + s));
    }

    // ----- COURSE MENU -----
    private void courseMenu() {
        while (true) {
            System.out.println("\n-- Courses --");
            System.out.println(" 1) Add course");
            System.out.println(" 2) Update course");
            System.out.println(" 3) Remove course");
            System.out.println(" 4) List courses");
            System.out.println(" 5) Search (by code/title)");
            System.out.println(" 6) Sort (code/title/credits)");
            System.out.println(" 0) Back");
            int ch = readInt("Choose", 0, 6);
            if (ch == 0) return;
            switch (ch) {
                case 1 -> doAddCourse();
                case 2 -> doUpdateCourse();
                case 3 -> doRemoveCourse();
                case 4 -> listCourses();
                case 5 -> searchCourses();
                case 6 -> sortCourses();
                default -> System.out.println("Unknown option");
            }
        }
    }

    private void doAddCourse() {
        System.out.println("\nAdd Course:");
        String code = readNonEmpty("Code (e.g., CS101)");
        String title = readNonEmpty("Title");
        int credits = readInt("Credits (1-6)", 1, 6);
        Course c = svc.addCourse(code, title, credits);
        System.out.println("Added: " + c);
    }

    private void doUpdateCourse() {
        int id = readInt("Course ID", 1, Integer.MAX_VALUE);
        Optional<Course> opt = svc.getCourse(id);
        if (opt.isEmpty()) { System.out.println("Not found"); return; }
        System.out.println("Editing: " + opt.get());
        String code = readOptional("New code (empty=keep)");
        String title = readOptional("New title (empty=keep)");
        String cr = readOptional("New credits (1-6, empty=keep)");
        Integer credits = null;
        if (cr != null && !cr.isBlank()) {
            try {
                int v = Integer.parseInt(cr.trim());
                if (v < 1 || v > 6) throw new RuntimeException();
                credits = v;
            } catch (Exception e) { System.out.println("Invalid credits"); }
        }
        boolean ok = svc.updateCourse(id, emptyToNull(code), emptyToNull(title), credits);
        System.out.println(ok ? "Updated." : "Failed.");
    }

    private void doRemoveCourse() {
        int id = readInt("Course ID", 1, Integer.MAX_VALUE);
        boolean ok = svc.removeCourse(id);
        System.out.println(ok ? "Removed." : "Not found.");
    }

    private void listCourses() {
        System.out.println("\nCourses:");
        for (Course c : svc.listCourses()) System.out.println(" - " + c);
    }

    private void searchCourses() {
        String key = readNonEmpty("Search text").toLowerCase();
        svc.listCourses().stream()
                .filter(c -> c.getCode().toLowerCase().contains(key) || c.getTitle().toLowerCase().contains(key))
                .forEach(c -> System.out.println(" - " + c));
    }

    private void sortCourses() {
        System.out.println("Sort by: 1) code  2) title  3) credits");
        int k = readInt("Choose", 1, 3);
        List<Course> list = svc.listCourses();
        switch (k) {
            case 1 -> list.sort(Comparator.comparing(Course::getCode));
            case 2 -> list.sort(Comparator.comparing(Course::getTitle));
            case 3 -> list.sort(Comparator.comparingInt(Course::getCredits));
        }
        list.forEach(c -> System.out.println(" - " + c));
    }

    // ----- ENROLLMENT MENU -----
    private void enrollmentMenu() {
        while (true) {
            System.out.println("\n-- Enrollments & Grades --");
            System.out.println(" 1) Enroll student to course");
            System.out.println(" 2) Assign/Update grade");
            System.out.println(" 3) Drop enrollment");
            System.out.println(" 4) List enrollments (by student)");
            System.out.println(" 5) List enrollments (by course)");
            System.out.println(" 0) Back");
            int ch = readInt("Choose", 0, 5);
            if (ch == 0) return;
            switch (ch) {
                case 1 -> doEnroll();
                case 2 -> doAssignGrade();
                case 3 -> doDropEnrollment();
                case 4 -> listEnrollmentsByStudent();
                case 5 -> listEnrollmentsByCourse();
                default -> System.out.println("Unknown option");
            }
        }
    }

    private void doEnroll() {
        int sid = readInt("Student ID", 1, Integer.MAX_VALUE);
        int cid = readInt("Course ID", 1, Integer.MAX_VALUE);
        try {
            Enrollment e = svc.enroll(sid, cid);
            System.out.println("Enrolled: " + e);
        } catch (Exception ex) {
            System.out.println("[ERROR] " + ex.getMessage());
        }
    }

    private void doAssignGrade() {
        int eid = readInt("Enrollment ID", 1, Integer.MAX_VALUE);
        Grade g = chooseGrade();
        boolean ok = svc.assignGrade(eid, g);
        System.out.println(ok ? "Updated." : "Not found.");
    }

    private void doDropEnrollment() {
        int eid = readInt("Enrollment ID", 1, Integer.MAX_VALUE);
        boolean ok = svc.dropEnrollment(eid);
        System.out.println(ok ? "Dropped." : "Not found.");
    }

    private void listEnrollmentsByStudent() {
        int sid = readInt("Student ID", 1, Integer.MAX_VALUE);
        double gpa = svc.computeGpa(sid);
        System.out.println("Enrollments for student #" + sid + " (GPA=" + String.format(Locale.US, "%.2f", gpa) + "):");
        for (Enrollment e : svc.listEnrollmentsForStudent(sid)) System.out.println(" - " + prettyEnrollment(e));
    }

    private void listEnrollmentsByCourse() {
        int cid = readInt("Course ID", 1, Integer.MAX_VALUE);
        System.out.println("Enrollments for course #" + cid + ":");
        for (Enrollment e : svc.listEnrollmentsForCourse(cid)) System.out.println(" - " + prettyEnrollment(e));
    }

    private String prettyEnrollment(Enrollment e) {
        String sname = svc.getStudent(e.getStudentId()).map(Student::getName).orElse("?");
        String cname = svc.getCourse(e.getCourseId()).map(Course::getCode).orElse("?");
        return String.format("%s -> %s  | on: %s | grade: %s (id=%d)",
                sname, cname, e.getEnrolledOn(), e.getGrade() == null ? "-" : e.getGrade().name(), e.getId());
    }

    // ----- ANALYTICS -----
    private void analyticsMenu() {
        while (true) {
            System.out.println("\n-- Analytics & Reports --");
            System.out.println(" 1) Course popularity");
            System.out.println(" 2) Top students by GPA");
            System.out.println(" 3) Transcript (student-wise)");
            System.out.println(" 0) Back");
            int ch = readInt("Choose", 0, 3);
            if (ch == 0) return;
            switch (ch) {
                case 1 -> reportCoursePopularity();
                case 2 -> reportTopStudents();
                case 3 -> reportTranscript();
                default -> System.out.println("Unknown option");
            }
        }
    }

    private void reportCoursePopularity() {
        System.out.println("\nCourse Popularity:");
        Map<Course, Integer> pop = svc.coursePopularity();
        pop.forEach((c, count) -> System.out.printf(" - %-10s | %-30s | students: %d%n", c.getCode(), c.getTitle(), count));
    }

    private void reportTopStudents() {
        int n = readInt("Show top N", 1, 50);
        List<StudentGpa> list = svc.topStudents(n);
        int rank = 1;
        for (StudentGpa sg : list) {
            System.out.printf(" %2d) %-20s GPA=%.2f (id=%d)%n", rank++, sg.student.getName(), sg.gpa, sg.student.getId());
        }
    }

    private void reportTranscript() {
        int sid = readInt("Student ID", 1, Integer.MAX_VALUE);
        Optional<Student> opt = svc.getStudent(sid);
        if (opt.isEmpty()) { System.out.println("Not found"); return; }
        Student s = opt.get();
        System.out.println("\nTranscript for " + s.getName() + " (#" + s.getId() + ")");
        System.out.println("DOB: " + (s.getDob() == null ? "-" : s.getDob()) + " | Email: " + (s.getEmail() == null ? "-" : s.getEmail()));
        double totalPts = 0, totalCr = 0;
        for (Enrollment e : svc.listEnrollmentsForStudent(sid)) {
            Course c = svc.getCourse(e.getCourseId()).orElse(null);
            String code = c == null ? "?" : c.getCode();
            int cr = c == null ? 0 : c.getCredits();
            Grade g = e.getGrade();
            double pts = (g == null ? 0 : g.points) * cr;
            totalPts += pts; totalCr += cr;
            System.out.printf(" - %-10s | credits=%d | grade=%s%n", code, cr, g == null ? "-" : g.name());
        }
        double gpa = totalCr == 0 ? 0 : totalPts / totalCr;
        System.out.printf("Total Credits: %.0f | GPA: %.2f%n", totalCr, gpa);
    }

    // ----- IMPORT / EXPORT -----
    private void importExportMenu() {
        while (true) {
            System.out.println("\n-- Import/Export CSV --");
            System.out.println(" 1) Import Students CSV");
            System.out.println(" 2) Import Courses CSV");
            System.out.println(" 3) Export Students CSV");
            System.out.println(" 4) Export Courses CSV");
            System.out.println(" 0) Back");
            int ch = readInt("Choose", 0, 4);
            if (ch == 0) return;
            try {
                switch (ch) {
                    case 1 -> { String f = readNonEmpty("File path"); svc.importStudentsCsv(f); System.out.println("Imported."); }
                    case 2 -> { String f = readNonEmpty("File path"); svc.importCoursesCsv(f); System.out.println("Imported."); }
                    case 3 -> { String f = readNonEmpty("Save to (students.csv)"); svc.exportStudentsCsv(f); System.out.println("Exported."); }
                    case 4 -> { String f = readNonEmpty("Save to (courses.csv)"); svc.exportCoursesCsv(f); System.out.println("Exported."); }
                    default -> System.out.println("Unknown option");
                }
            } catch (IOException ioe) {
                System.out.println("[IO ERROR] " + ioe.getMessage());
            }
        }
    }

    // ----- MISC -----
    private void listAllData() {
        System.out.println("\n=== STUDENTS ===");
        listStudents();
        System.out.println("\n=== COURSES ===");
        listCourses();
        System.out.println("\n=== ENROLLMENTS ===");
        for (Enrollment e : store.enrollments) System.out.println(" - " + prettyEnrollment(e));
    }

    private void saveNow() {
        try { svc.save(); System.out.println("Saved to default file."); }
        catch (Exception e) { System.out.println("[ERROR] " + e.getMessage()); }
    }

    private void backupTo(String file) {
        try { svc.saveAs(file); System.out.println("Backup saved to: " + file); }
        catch (Exception e) { System.out.println("[ERROR] " + e.getMessage()); }
    }

    private void restoreFromPrompt() {
        String file = readNonEmpty("Restore from file path");
        DataStore loaded = Storage.loadOrNew(file);
        this.store = loaded;
        this.um = new UndoManager();
        this.svc = new UniversityService(store, um);
        System.out.println("Restored. (Note: undo/redo history cleared)");
    }

    private void help() {
        System.out.println("\nHelp:");
        System.out.println(" - Use numbers to choose menu options.");
        System.out.println(" - IDs are shown when you list students/courses/enrollments.");
        System.out.println(" - Undo/Redo supports most CRUD operations.");
        System.out.println(" - Data is saved when you choose Save or Backup.");
    }

    private void undo() { System.out.println(svc.undo() ? "Undone." : "Nothing to undo."); }
    private void redo() { System.out.println(svc.redo() ? "Redone." : "Nothing to redo."); }

    private void exit() {
        System.out.println("\nExiting... do you want to save before exit? (y/n)");
        String ans = in.nextLine().trim().toLowerCase();
        if (ans.startsWith("y")) saveNow();
        System.out.println("Bye!");
    }

    // ----- INPUT HELPERS -----
    private int readInt(String label, int min, int max) {
        while (true) {
            System.out.print(label + ": ");
            String s = in.nextLine();
            try {
                int val = Integer.parseInt(s.trim());
                if (val < min || val > max) throw new NumberFormatException();
                return val;
            } catch (Exception e) { System.out.println("Enter a number in [" + min + "," + max + "]"); }
        }
    }

    private LocalDate readDate(String label) {
        while (true) {
            System.out.print(label + ": ");
            String s = in.nextLine();
            try { return LocalDate.parse(s.trim(), DF); } catch (Exception e) { System.out.println("Format yyyy-MM-dd"); }
        }
    }

    private LocalDate readOptionalDate(String label) {
        System.out.print(label + ": ");
        String s = in.nextLine();
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim(), DF); } catch (Exception e) { System.out.println("Invalid date, keeping old."); return null; }
    }

    private String readNonEmpty(String label) {
        while (true) {
            System.out.print(label + ": ");
            String s = in.nextLine();
            if (s != null && !s.isBlank()) return s.trim();
            System.out.println("Required.");
        }
    }

    private String readOptional(String label) {
        System.out.print(label + ": ");
        String s = in.nextLine();
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String emptyToNull(String s) { return (s == null || s.isBlank()) ? null : s; }

    private Grade chooseGrade() {
        System.out.println("Grade: 1) A  2) A-  3) B+  4) B  5) C  6) D  7) E  8) F");
        int k = readInt("Choose", 1, 8);
        return switch (k) {
            case 1 -> Grade.A;
            case 2 -> Grade.A_MINUS;
            case 3 -> Grade.B_PLUS;
            case 4 -> Grade.B;
            case 5 -> Grade.C;
            case 6 -> Grade.D;
            case 7 -> Grade.E;
            default -> Grade.F;
        };
    }
}
