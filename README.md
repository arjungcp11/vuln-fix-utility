# vuln-fix-utility
🔐 SonarQube Java Vulnerabilities – Code-Level List
1️⃣ String comparison using ==

Rule: java:S4973

❌ Bad

if (status == "SUCCESS") {
}


✅ Fix

if ("SUCCESS".equals(status)) {
}
// OR
Objects.equals(status, "SUCCESS");

2️⃣ NullPointerException risk

Rule: java:S2259

❌ Bad

user.getName().equals("ADMIN");


✅ Fix

"ADMIN".equals(user.getName());

3️⃣ Hardcoded credentials

Rule: java:S2068

❌ Bad

String password = "admin123";


✅ Fix

String password = System.getenv("APP_PASSWORD");

4️⃣ Hardcoded sensitive URLs / tokens

Rule: java:S6418

❌ Bad

String token = "eyJhbGciOiJIUzI1NiJ9";


✅ Fix

@Value("${security.token}")
private String token;

5️⃣ SQL Injection

Rule: java:S3649

❌ Bad

String sql = "SELECT * FROM users WHERE id=" + id;


✅ Fix

String sql = "SELECT * FROM users WHERE id=?";
PreparedStatement ps = conn.prepareStatement(sql);
ps.setInt(1, id);

6️⃣ Path Traversal

Rule: java:S2083

❌ Bad

new File(userInput);


✅ Fix

Path base = Paths.get("/safe/dir");
Path resolved = base.resolve(userInput).normalize();

7️⃣ Command Injection

Rule: java:S2076

❌ Bad

Runtime.getRuntime().exec(userInput);


✅ Fix

new ProcessBuilder("ls", "-l").start();

8️⃣ Insecure Random

Rule: java:S2245

❌ Bad

Random random = new Random();


✅ Fix

SecureRandom random = new SecureRandom();

9️⃣ Weak cryptography (MD5 / SHA1)

Rule: java:S4790

❌ Bad

MessageDigest md = MessageDigest.getInstance("MD5");


✅ Fix

MessageDigest md = MessageDigest.getInstance("SHA-256");

🔟 Logging sensitive data

Rule: java:S2065

❌ Bad

log.info("Password: {}", password);


✅ Fix

log.info("User login attempt");

1️⃣1️⃣ Use of System.out.println

Rule: java:S106

❌ Bad

System.out.println("Started");


✅ Fix

private static final Logger log = LoggerFactory.getLogger(MyClass.class);
log.info("Started");

1️⃣2️⃣ Empty catch block

Rule: java:S108

❌ Bad

catch (Exception e) {
}


✅ Fix

catch (Exception e) {
    log.error("Error occurred", e);
}

1️⃣3️⃣ Swallowed exceptions

Rule: java:S1166

❌ Bad

catch (Exception e) {
    throw new RuntimeException();
}


✅ Fix

catch (Exception e) {
    throw new RuntimeException(e);
}

1️⃣4️⃣ Unclosed resources

Rule: java:S2095

❌ Bad

FileInputStream fis = new FileInputStream(file);


✅ Fix

try (FileInputStream fis = new FileInputStream(file)) {
}

1️⃣5️⃣ Equals and hashCode mismatch

Rule: java:S1206

❌ Bad

class User {
    public boolean equals(Object o) { ... }
}


✅ Fix

@Override
public int hashCode() { ... }

1️⃣6️⃣ Exposing internal mutable state

Rule: java:S2384

❌ Bad

public Date getDate() {
    return date;
}


✅ Fix

return new Date(date.getTime());

1️⃣7️⃣ Public static mutable fields

Rule: java:S2386

❌ Bad

public static List<String> DATA = new ArrayList<>();


✅ Fix

private static final List<String> DATA = List.of();

1️⃣8️⃣ Serializable without serialVersionUID

Rule: java:S2065

❌ Bad

class User implements Serializable {
}


✅ Fix

private static final long serialVersionUID = 1L;

1️⃣9️⃣ Too generic exception

Rule: java:S112

❌ Bad

throws Exception


✅ Fix

throws IOException

2️⃣0️⃣ Missing validation on user input

Rule: java:S5131

❌ Bad

@RequestParam String name


✅ Fix

@Size(max = 50)
@RequestParam String name

