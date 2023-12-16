package scheduler;

import scheduler.db.ConnectionManager;
import scheduler.model.Caregiver;
import scheduler.model.Patient;
import scheduler.model.Vaccine;
import scheduler.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;

public class Scheduler {

    // objects to keep track of the currently logged-in user
    // Note: it is always true that at most one of currentCaregiver and currentPatient is not null
    //       since only one user can be logged-in at a time
    private static Caregiver currentCaregiver = null;
    private static Patient currentPatient = null;

    public static void main(String[] args) {
        // printing greetings text
        System.out.println();
        System.out.println("Welcome to the COVID-19 Vaccine Reservation Scheduling Application!");
        System.out.println("*** Please enter one of the following commands ***");
        System.out.println("> create_patient <username> <password>");  //TODO: implement create_patient (Part 1)
        System.out.println("> create_caregiver <username> <password>");
        System.out.println("> login_patient <username> <password>");  // TODO: implement login_patient (Part 1)
        System.out.println("> login_caregiver <username> <password>");
        System.out.println("> search_caregiver_schedule <date>");  // TODO: implement search_caregiver_schedule (Part 2)
        System.out.println("> reserve <date> <vaccine>");  // TODO: implement reserve (Part 2)
        System.out.println("> upload_availability <date>");
        System.out.println("> cancel <appointment_id>");  // TODO: implement cancel (extra credit)
        System.out.println("> add_doses <vaccine> <number>");
        System.out.println("> show_appointments");  // TODO: implement show_appointments (Part 2)
        System.out.println("> logout");  // TODO: implement logout (Part 2)
        System.out.println("> quit");
        System.out.println();


        // read input from user
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("> ");
            String response = "";
            try {
                response = r.readLine();
            } catch (IOException e) {
                System.out.println("Please try again!");
            }
            // split the user input by spaces
            String[] tokens = response.split(" ");
            // check if input exists
            if (tokens.length == 0) {
                System.out.println("Please try again!");
                continue;
            }
            // determine which operation to perform
            String operation = tokens[0];
            if (operation.equals("create_patient")) {
                createPatient(tokens);
            } else if (operation.equals("create_caregiver")) {
                createCaregiver(tokens);
            } else if (operation.equals("login_patient")) {
                loginPatient(tokens);
            } else if (operation.equals("login_caregiver")) {
                loginCaregiver(tokens);
            } else if (operation.equals("search_caregiver_schedule")) {
                searchCaregiverSchedule(tokens);
            } else if (operation.equals("reserve")) {
                reserve(tokens);
            } else if (operation.equals("upload_availability")) {
                uploadAvailability(tokens);
            } else if (operation.equals("cancel")) {
                cancel(tokens);
            } else if (operation.equals("add_doses")) {
                addDoses(tokens);
            } else if (operation.equals("show_appointments")) {
                showAppointments(tokens);
            } else if (operation.equals("logout")) {
                logout(tokens);
            } else if (operation.equals("quit")) {
                System.out.println("Bye!");
                return;
            } else {
                System.out.println("Invalid operation name!");
            }
        }
    }

    private static void createPatient(String[] tokens) {
        if (tokens.length != 3) {
            System.out.println("Failed to create user.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        if (usernameExistsPatient(username)) {
            System.out.println("Username taken, try again!");
            return;
        }
        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);

        try {
            Patient patient = new Patient.PatientBuilder(username, salt, hash).build();

            patient.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Failed to create user.");
            e.printStackTrace();
        }
    }

    private static boolean usernameExistsPatient(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Patients WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void createCaregiver(String[] tokens) {
        // create_caregiver <username> <password>
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Failed to create user.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        // check 2: check if the username has been taken already
        if (usernameExistsCaregiver(username)) {
            System.out.println("Username taken, try again!");
            return;
        }
        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        // create the caregiver
        try {
            Caregiver caregiver = new Caregiver.CaregiverBuilder(username, salt, hash).build(); 
            // save to caregiver information to our database
            caregiver.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Failed to create user.");
            e.printStackTrace();
        }
    }

    private static boolean usernameExistsCaregiver(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Caregivers WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void loginPatient(String[] tokens) {
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in.");
            return;
        }
        if (tokens.length != 3) {
            System.out.println("Login failed.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Patient patient = null;
        try {
            patient = new Patient.PatientGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }
        if (patient == null) {
            System.out.println("Login failed.");
        } else {
            System.out.println("Logged in as: " + username);
            currentPatient = patient;
        }
    }

    private static void loginCaregiver(String[] tokens) {
        // login_caregiver <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in.");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Login failed.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Caregiver caregiver = null;
        try {
            caregiver = new Caregiver.CaregiverGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }
        // check if the login was successful
        if (caregiver == null) {
            System.out.println("Login failed.");
        } else {
            System.out.println("Logged in as: " + username);
            currentCaregiver = caregiver;
        }
    }

    private static void searchCaregiverSchedule(String[] tokens) {
        // search_caregiver_schedule <date>
        // check 1: check if currently logged-in
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String date = tokens[1];
        String selectCaregivers = "SELECT A.Username, V.Name, V.Doses FROM Availabilities AS A, " +
                                  "Vaccines AS V WHERE A.Time = ? AND V.Doses > 0 ORDER BY A.Username";
        try {
            PreparedStatement selectStatement = con.prepareStatement(selectCaregivers);
            selectStatement.setString(1, date);
            ResultSet caregiverResultSet = selectStatement.executeQuery();
            printResultSet(caregiverResultSet, 3);
        } catch (SQLException e) {
            System.out.println("Please try again!");
        } finally {
            cm.closeConnection();
        }
    }

    private static void reserve(String[] tokens) {
        // TODO: Part 2
        // reserve <date> vaccine
        // check 1: check if currently logged-in
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }
        // check 2: check if the current logged-in user is a patient
        if (currentPatient == null) {
            System.out.println("Please login as a patient!");
            return;
        }
        // check 3: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        String date = tokens[1];
        String vaccineName = tokens[2];

        String checkAvailableCaregiver = "SELECT Username FROM Availabilities WHERE Time = ? ORDER BY Username";
        String checkAvailableDose = "SELECT * FROM Vaccines WHERE Name = ? AND Doses >= 0";
        String addAppointment = "INSERT INTO Appointments VALUES (?, ?, ?, ?)";
        String selectAppointment = "SELECT Appointment_id, Caregiver_name FROM Appointments WHERE " +
                                    "Time = ? AND Caregiver_name = ?";
        String removeAvailability = "DELETE FROM Availabilities WHERE Time = ? AND Username = ?";
        try {
            PreparedStatement checkAvailableCaregiverStatement = con.prepareStatement(checkAvailableCaregiver);
            checkAvailableCaregiverStatement.setString(1, date);
            ResultSet availableCaregivers = checkAvailableCaregiverStatement.executeQuery();
            // check 4: check if there are available caregivers for that date
            if (!availableCaregivers.isBeforeFirst()) {
                System.out.println("No Caregiver is available!");
                return;
            }
            // check 5: check if there are enough vaccine doses
            PreparedStatement checkAvailableDoseStatement = con.prepareStatement(checkAvailableDose);
            checkAvailableDoseStatement.setString(1, vaccineName);
            ResultSet availableDoses = checkAvailableDoseStatement.executeQuery();
            if (!availableDoses.isBeforeFirst()) {
                System.out.println("Not enough available doses!");
                return;
            }

            // upload the appointment
            availableCaregivers.next();
            String caregiverName = availableCaregivers.getString(1);
            PreparedStatement addAppointmentStatement = con.prepareStatement(addAppointment);
            addAppointmentStatement.setString(1, currentPatient.getUsername());
            addAppointmentStatement.setString(2, caregiverName);
            addAppointmentStatement.setString(3, vaccineName);
            addAppointmentStatement.setString(4, date);
            addAppointmentStatement.executeUpdate();

            // print out the appointment information, including appointment id and caregiver name
            PreparedStatement selectAppointmentStatement = con.prepareStatement(selectAppointment);
            selectAppointmentStatement.setString(1, date);
            selectAppointmentStatement.setString(2, caregiverName);
            ResultSet appointment = selectAppointmentStatement.executeQuery();
            appointment.next();
            System.out.println("Appointment ID: " + appointment.getString(1) +
                               ", Caregiver username: " + appointment.getString(2));

            // remove availability for the selected caregiver
            PreparedStatement removeAvailabilityStatement = con.prepareStatement(removeAvailability);
            removeAvailabilityStatement.setString(1, date);
            removeAvailabilityStatement.setString(2, caregiverName);
            removeAvailabilityStatement.executeUpdate();

            // decrease vaccine doses by 1
            Vaccine vaccine = new Vaccine.VaccineGetter(vaccineName).get();
            vaccine.decreaseAvailableDoses(1);
        } catch (SQLException e) {
            System.out.println("Please try again!");
        } finally {
            cm.closeConnection();
        }
    }

    private static void uploadAvailability(String[] tokens) {
        // upload_availability <date>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }
        String date = tokens[1];
        try {
            Date d = Date.valueOf(date);
            currentCaregiver.uploadAvailability(d);
            System.out.println("Availability uploaded!");
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        } catch (SQLException e) {
            System.out.println("Error occurred when uploading availability");
            e.printStackTrace();
        }
    }

    private static void cancel(String[] tokens) {
        // TODO: Extra credit
        // cancel <appointment_id>
        // check 1: check if currently logged in
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        String appointmentID = tokens[1];

        String selectAppointment = "SELECT * FROM Appointments WHERE Appointment_id = ?";
        String deleteAppointment = "DELETE FROM Appointments WHERE Appointment_id = ?";
        String uploadAppointment = "INSERT INTO Availabilities VALUES (?, ?)";
        try {
            // select the appointment
            PreparedStatement selectAppointmentStatement = con.prepareStatement(selectAppointment);
            selectAppointmentStatement.setString(1, appointmentID);
            ResultSet appointment = selectAppointmentStatement.executeQuery();
            if (!appointment.isBeforeFirst()) {
                System.out.println("Appointment does not exist!");
                return;
            }
            appointment.next();

            // record the caregiver username and date
            String caregiverUsername = appointment.getString(3);
            String vaccineName = appointment.getString(4);
            String date = appointment.getString(5);

            // upload availability for the caregiver and date
            PreparedStatement uploadAvailabilityStatement = con.prepareStatement(uploadAppointment);
            uploadAvailabilityStatement.setString(1, date);
            uploadAvailabilityStatement.setString(2, caregiverUsername);
            uploadAvailabilityStatement.executeUpdate();

            // delete the appointment
            PreparedStatement deleteAppointmentStatement = con.prepareStatement(deleteAppointment);
            deleteAppointmentStatement.setString(1, appointmentID);
            deleteAppointmentStatement.executeUpdate();

            // decrease vaccine doses by 1
            Vaccine vaccine = new Vaccine.VaccineGetter(vaccineName).get();
            vaccine.increaseAvailableDoses(1);
            System.out.println("Successfully canceled!");
        } catch (SQLException e) {
            System.out.println("Please try again!");
        } finally {
            cm.closeConnection();
        }

    }

    private static void addDoses(String[] tokens) {
        // add_doses <vaccine> <number>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String vaccineName = tokens[1];
        int doses = Integer.parseInt(tokens[2]);
        Vaccine vaccine = null;
        try {
            vaccine = new Vaccine.VaccineGetter(vaccineName).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when adding doses");
            e.printStackTrace();
        }
        // check 3: if getter returns null, it means that we need to create the vaccine and insert it into the Vaccines
        //          table
        if (vaccine == null) {
            try {
                vaccine = new Vaccine.VaccineBuilder(vaccineName, doses).build();
                vaccine.saveToDB();
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        } else {
            // if the vaccine is not null, meaning that the vaccine already exists in our table
            try {
                vaccine.increaseAvailableDoses(doses);
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        }
        System.out.println("Doses updated!");
    }

    private static void showAppointments(String[] tokens) {
        // TODO: Part 2
        // check 1: check if currently logged in
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 1 without extra word
        if (tokens.length != 1) {
            System.out.println("Please try again!");
            return;
        }

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectAppointments = "";
        String currentUsername = "";
        if (currentCaregiver != null) {
            selectAppointments = "SELECT Appointment_id, Vaccine_name, Time, Patient_name " +
                    "FROM Appointments WHERE Caregiver_name = ? ORDER BY Appointment_id";
            currentUsername = currentCaregiver.getUsername();
        } else {
            selectAppointments = "SELECT Appointment_id, Vaccine_name, Time, Caregiver_name " +
                    "FROM Appointments WHERE Patient_name = ? ORDER BY Appointment_id";
            currentUsername = currentPatient.getUsername();
        }
        try {
            PreparedStatement selectAppointmentsStatement = con.prepareStatement(selectAppointments);
            selectAppointmentsStatement.setString(1, currentUsername);
            ResultSet appointments = selectAppointmentsStatement.executeQuery();
            printResultSet(appointments, 4);
        } catch (SQLException e) {
            System.out.println("Please try again!");
        } finally {
            cm.closeConnection();
        }
    }

    private static void logout(String[] tokens) {
        // check 1: check if currently logged in
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 1 without extra word
        if (tokens.length != 1) {
            System.out.println("Please try again!");
            return;
        }
        currentPatient = null;
        currentCaregiver = null;
        System.out.println("Successfully logged out!");
    }

    //print the result set with given number of columns to console, separate by space
    private static void printResultSet(ResultSet rs, int columnsNumber) throws SQLException {
        while (rs.next()) {
            for (int i = 1; i <= columnsNumber; i++) {
                if (i > 1) System.out.print(" ");
                System.out.print(rs.getString(i));
            }
            System.out.println("");
        }
    }
}
