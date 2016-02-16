package com.autonomy.abc.selenium.page.admin;

import com.autonomy.abc.selenium.element.FormInput;
import com.autonomy.abc.selenium.element.GritterNotice;
import com.autonomy.abc.selenium.users.*;
import com.autonomy.abc.selenium.util.ParametrizedFactory;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class HSOUsersPage extends HSOUserManagementPage {
    private HSOUsersPage(WebDriver driver) {
        super(driver);
        waitForLoad();
    }

    @Override
    public HSOUser addNewUser(NewUser newUser, Role role) {
        if (newUser instanceof HSONewUser) {
            return addHSONewUser((HSONewUser) newUser, role);
        }
        throw new IllegalStateException("Cannot create new user " + newUser);
    }

    private HSOUser addHSONewUser(HSONewUser newUser, Role role) {
        addUsername(newUser.getUsername());
        addEmail(newUser.getEmail());
        selectRole(role);
        createButton().click();

        try {
            new WebDriverWait(getDriver(), 15)
                    .withMessage("User hasn't been created")
                    .until(GritterNotice.notificationContaining("Created user"));
            new WebDriverWait(getDriver(), 5)
                    .withMessage("User input hasn't cleared")
                    .until(new ExpectedCondition<Boolean>() {
                        @Override
                        public Boolean apply(WebDriver driver) {
                            return getUsernameInput().getValue().isEmpty();
                        }
                    });
        } catch (TimeoutException e) {
            throw new HSONewUser.UserNotCreatedException(newUser);
        }

        return newUser.withRole(role);
    }

    public WebElement getUserRow(User user){
        return findElement(By.xpath("//*[contains(@class,'user-email') and text()='" + ((HSOUser) user).getEmail() + "']/.."));
    }

    public FormInput getUsernameInput(){
        return new FormInput(getDriver().findElement(By.id("create-users-username")), getDriver());
    }

    public FormInput getEmailInput(){
        return new FormInput(getDriver().findElement(By.className("create-user-email-input")), getDriver());
    }

    public WebElement userLevelDropdown(){
        return getDriver().findElement(By.id("create-users-role"));
    }

    public void addEmail(String email) {
        getEmailInput().setValue(email);
    }

    public void setRoleValueFor(User user, Role newRole) {
        getUserRow(user).findElement(By.xpath(".//a[contains(text(),'"+newRole+"')]")).click();
    }

    public String getEmailOf(User user) {
        return getUserRow(user).findElement(By.className("user-email")).getText();
    }

    public WebElement resetAuthenticationButton(User user) {
        return getUserRow(user).findElement(By.className("reset-authentication"));
    }

    public void clearEmail() {
        getEmailInput().clear();
    }

    public static class Factory implements ParametrizedFactory<WebDriver, HSOUsersPage> {
        public HSOUsersPage create(WebDriver context) {
            return new HSOUsersPage(context);
        }
    }
}
