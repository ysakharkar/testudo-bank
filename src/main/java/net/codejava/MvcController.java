package net.codejava;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;


import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

@Controller
public class MvcController {
  /**
   * A simplified JDBC client that is injected with the login credentials
   * specified in /src/main/resources/application.properties
   */
  private JdbcTemplate jdbcTemplate;

  public MvcController(@Autowired JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * HTML GET request handler that serves the "welcome" page to the user.
   * 
   * @param model
   * @return "welcome" page
   */
	@GetMapping("/")
	public String showWelcome(Model model) {
		return "welcome";
	}

  /**
   * HTML GET request handler that serves the "login_form" page to the user.
   * An empty `User` object is also added to the Model as an Attribute to store
   * the user's login form input.
   * 
   * @param model
   * @return "login_form" page
   */
  @GetMapping("/login")
	public String showLoginForm(Model model) {
		User user = new User();
		model.addAttribute("user", user);
		
		return "login_form";
	}

  /**
   * Helper method that queries the MySQL DB for the customer account info (First Name, Last Name, and Balance)
   * and adds these values to the `user` Model Attribute so that they can be displayed in the "account_info" page.
   * 
   * @param user
   */
  private void updateAccountInfo(User user) {
    String getUserNameAndBalanceSql = String.format("SELECT FirstName, LastName, Balance, OverdraftBalance FROM customers WHERE CustomerID='%s';", user.getUsername());
    List<Map<String,Object>> queryResults = jdbcTemplate.queryForList(getUserNameAndBalanceSql);
    String getOverDraftLogsSql = String.format("SELECT * FROM OverdraftLogs WHERE CustomerID='%s';", user.getUsername());
    
    List<Map<String,Object>> queryLogs = jdbcTemplate.queryForList(getOverDraftLogsSql);
    String logs = "<br/>";
    for(Map<String, Object> x: queryLogs){
      logs += x + "<br/>";
    }
    Map<String,Object> userData = queryResults.get(0);

    user.setFirstName((String)userData.get("FirstName"));
    user.setLastName((String)userData.get("LastName"));
    user.setBalance((int)userData.get("Balance"));
    user.setOverDraftBalance((int)userData.get("OverdraftBalance"));
    user.setLogs(logs);
  }

  /**
   * HTML POST request handler that uses user input from Login Form page to determine 
   * login success or failure.
   * 
   * Queries 'passwords' table in MySQL DB for the correct password associated with the
   * username ID given by the user. Compares the user's password attempt with the correct
   * password.
   * 
   * If the password attempt is correct, the "account_info" page is served to the customer
   * with all account details retrieved from the MySQL DB.
   * 
   * If the password attempt is incorrect, the user is redirected to the "welcome" page.
   * 
   * @param user
   * @return "account_info" page if login successful. Otherwise, redirect to "welcome" page.
   */
  @PostMapping("/login")
	public String submitLoginForm(@ModelAttribute("user") User user) {
    // Print user's existing fields for debugging
		System.out.println(user);

    String userID = user.getUsername();
    String userPasswordAttempt = user.getPassword();

    // Retrieve correct password for this customer.
    String getUserPasswordSql = String.format("SELECT Password FROM passwords WHERE CustomerID='%s';", userID);
    String userPassword = jdbcTemplate.queryForObject(getUserPasswordSql, String.class);

    if (userPasswordAttempt.equals(userPassword)) {
      updateAccountInfo(user);

      return "account_info";
    } else {
      return "welcome";
    }
	}

  /**
   * HTML GET request handler that serves the "deposit_form" page to the user.
   * An empty `User` object is also added to the Model as an Attribute to store
   * the user's deposit form input.
   * 
   * @param model
   * @return "deposit_form" page
   */
  @GetMapping("/deposit")
	public String showDepositForm(Model model) {
    User user = new User();
		model.addAttribute("user", user);
		return "deposit_form";
	}

  /**
   * HTML POST request handler for the Deposit Form page.
   * 
   * The same username+password handling from the login page is used.
   * 
   * If the password attempt is correct, the balance is incremented by the amount specified
   * in the Deposit Form. The user is then served the "account_info" with an updated balance.
   * 
   * If the password attempt is incorrect, the user is redirected to the "welcome" page.
   * 
   * @param user
   * @return "account_info" page if login successful. Otherwise, redirect to "welcome" page.
   */
  @PostMapping("/deposit")
  public String submitDeposit(@ModelAttribute("user") User user) {
		System.out.println(user); // Print user's existing fields for debugging

    String userID = user.getUsername();
    String userPasswordAttempt = user.getPassword();

    // Retrieve correct password for this customer.
    String getUserPasswordSql = String.format("SELECT Password FROM passwords WHERE CustomerID='%s';", userID);
    String userPassword = jdbcTemplate.queryForObject(getUserPasswordSql, String.class);

    if (userPasswordAttempt.equals(userPassword)) {
      
      // Execute SQL Update command that increments user's Balance by given amount from the deposit form.

      if(user.getAmountToDeposit() < 0){
        return "welcome";
      }

      String getUserOverdraftBalanceSql = String.format("SELECT OverdraftBalance FROM customers WHERE CustomerID='%s';", userID);
      String userOverdraftBalance = jdbcTemplate.queryForObject(getUserOverdraftBalanceSql, String.class);


      int userODBalance = 0; // to confirm the passed value is integer
      if(Character.isDigit(userOverdraftBalance.charAt(0))){
        userODBalance = Integer.parseInt(userOverdraftBalance);
      }
      // if the overdraft balance is positive, subtract the deposit with interest
      if(userODBalance > 0){
        int leftOver = user.getAmountToDeposit() - Integer.parseInt(userOverdraftBalance); // remaining value after clearing overdraft
        float overDraftMoney = ((float)user.getAmountToDeposit())/1.02f;
        int setOverDraftBalance = Integer.parseInt(userOverdraftBalance) - (int)overDraftMoney;

        java.util.Date dt = new java.util.Date();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currentTime = sdf.format(dt);
        
        // inserting into the database information about payment log
        String overDraftInsertSql = String.format("INSERT INTO OverdraftLogs VALUES ('%s' , '%s', %d, %d, %d);", userID, currentTime,
        user.getAmountToDeposit(), Integer.parseInt(userOverdraftBalance), setOverDraftBalance);
        jdbcTemplate.update(overDraftInsertSql);

        // updating cusomters table
        String overDraftBalanceUpdateSql = String.format("UPDATE Customers SET OverdraftBalance = %d WHERE CustomerID='%s';", 
        setOverDraftBalance, userID);
        jdbcTemplate.update(overDraftBalanceUpdateSql);
        updateAccountInfo(user);
        if(leftOver > 0){
          String balanceIncreaseSql = String.format("UPDATE Customers SET Balance = Balance + %d WHERE CustomerID='%s';", leftOver/100, userID);
          System.out.println(balanceIncreaseSql); // Print executed SQL update for debugging
          jdbcTemplate.update(balanceIncreaseSql);

          updateAccountInfo(user);

          return "account_info";
        } else {
          return "account_info";
        }
      }

      String balanceIncreaseSql = String.format("UPDATE Customers SET Balance = Balance + %d WHERE CustomerID='%s';", user.getAmountToDeposit()/100, userID);
      System.out.println(balanceIncreaseSql); // Print executed SQL update for debugging
      jdbcTemplate.update(balanceIncreaseSql);

      updateAccountInfo(user);

      return "account_info";
    } else {
      return "welcome";
    }
  }
	
  /**
   * HTML GET request handler that serves the "withdraw_form" page to the user.
   * An empty `User` object is also added to the Model as an Attribute to store
   * the user's withdraw form input.
   * 
   * @param model
   * @return "withdraw_form" page
   */
  @GetMapping("/withdraw")
	public String showWithdrawForm(Model model) {
    User user = new User();
		model.addAttribute("user", user);
		return "withdraw_form";
	}

  /**
   * HTML POST request handler for the Withdraw Form page.
   * 
   * The same username+password handling from the login page is used.
   * 
   * If the password attempt is correct, the balance is decremented by the amount specified
   * in the Withdraw Form. The user is then served the "account_info" with an updated balance.
   * 
   * If the password attempt is incorrect, the user is redirected to the "welcome" page.
   * 
   * @param user
   * @return "account_info" page if login successful. Otherwise, redirect to "welcome" page.
   */
  @PostMapping("/withdraw")
  public String submitWithdraw(@ModelAttribute("user") User user) {
    String userID = user.getUsername();
    String userPasswordAttempt = user.getPassword();
    
    String getUserPasswordSql = String.format("SELECT Password FROM passwords WHERE CustomerID='%s';", userID);

    String userPassword = jdbcTemplate.queryForObject(getUserPasswordSql, String.class);

    if (userPasswordAttempt.equals(userPassword)) {
      // Execute SQL Update command that decrements Balance value for
      // user's row in Customers table using user.getAmountToWithdraw()

      if(user.getAmountToWithdraw() < 0){
        return "welcome";
      }
      String userBalanceSql =  String.format("SELECT Balance FROM customers WHERE CustomerID='%s';", userID);
      String userBalance = jdbcTemplate.queryForObject(userBalanceSql, String.class);
      
      int theUserBalance = 1000; // to confirm the passed value is integer
      if(Character.isDigit(userBalance.charAt(0))){
        theUserBalance = Integer.parseInt(userBalance);
      }
      // if the balance is not positive, withdraw with interest fee
      if(theUserBalance <= 0){
        String getUserOverdraftBalanceSql = String.format("SELECT OverdraftBalance FROM customers WHERE CustomerID='%s';", userID);
        String userOverdraftBalance = jdbcTemplate.queryForObject(getUserOverdraftBalanceSql, String.class);

        if(user.getAmountToWithdraw() + Integer.parseInt(userOverdraftBalance) <= 100000){
          int setOverDraftBalance = Integer.parseInt(userOverdraftBalance);

          String overDraftBalanceUpdateSql = String.format("UPDATE Customers SET OverdraftBalance = %d WHERE CustomerID='%s';", 
          setOverDraftBalance + user.getAmountToWithdraw(), userID);
          jdbcTemplate.update(overDraftBalanceUpdateSql);
          System.out.println(overDraftBalanceUpdateSql);
          updateAccountInfo(user);
          return "account_info";
        } else {
          return "welcome";
        }

      }

      String balanceIncreaseSql = String.format("UPDATE Customers SET Balance = Balance - %d WHERE CustomerID='%s';", user.getAmountToWithdraw()/100, userID);
      System.out.println(balanceIncreaseSql);
      jdbcTemplate.update(balanceIncreaseSql);

      updateAccountInfo(user);

      return "account_info";
    } else {
      return "welcome";
    }
  }
}