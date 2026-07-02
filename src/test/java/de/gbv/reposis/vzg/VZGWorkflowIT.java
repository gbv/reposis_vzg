/*
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *
 * MyCoRe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyCoRe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MyCoRe.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.gbv.reposis.vzg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mycore.common.selenium.MCRSeleniumTestBase;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/**
 * Tests the VZG workflow box requirements (see README section "Anforderungen Workflow"):
 * role based visibility, no edit link, delete only in submitted state, URN assignment and
 * publishing only with an uploaded document, publishing only for editors with an assigned URN
 * and an assigned license, handing over to review only with an assigned license.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VZGWorkflowIT extends MCRSeleniumTestBase {

    private static final String PPN = "198562268";

    private static final String TITLE_PART = "Ernährungsphysiologie";

    private static final String ADMIN_USER = "administrator";

    private static final String ADMIN_PASSWORD = "alleswirdgut";

    private static final String EDITOR_USER = "vzgeditor";

    private static final String CREATOR_USER = "vzgcreator";

    private static final String TEST_PASSWORD = "integrationtest";

    private static final String PUBLISH_LINK_TEXT = "Einreichung publizieren";

    private static final String URN_LINK_TEXT = "URN registrieren";

    private static final String DELETE_LINK_TEXT = "Löschen dieses Dokumentes";

    private static final String EDIT_LINK_TEXT = "Bearbeiten dieses Dokumentes";

    // the layout moves the box into the main column, the #mir-workflow wrapper is not part of the final HTML
    private static final By WORKFLOW_BOX = By.className("workflow-box");

    private static final By URN_OPTION = By.cssSelector(".workflow-box a[data-register-pi]");

    private static final By LICENSE_SELECT = By.cssSelector(".workflow-box .vzg-license-select select");

    private static String creatorObjectURL;

    private static String adminObjectURL;

    /**
     * Creates the test users through the user administration UI.
     */
    @Test
    public void test01AdminCreatesUsers() {
        logout();
        loginAs(ADMIN_USER, ADMIN_PASSWORD);
        createUser(EDITOR_USER, TEST_PASSWORD, "editor");
        createUser(CREATOR_USER, TEST_PASSWORD, "submitter");
        logout();
    }

    /**
     * Creator imports a document by PPN: box visible, no edit link, no URN option,
     * no publish option, hint to upload a document.
     */
    @Test
    public void test02CreatorImportsDocument() {
        loginAs(CREATOR_USER, TEST_PASSWORD);
        importPPN(false);
        creatorObjectURL = driver.getCurrentUrl();

        // right after the import the object is not yet indexed, the access strategy then denies
        // write access and the page renders without the box, so reload until solr caught up
        reloadUntilPresent(WORKFLOW_BOX);
        assertNoLink(EDIT_LINK_TEXT);
        assertNoLink(PUBLISH_LINK_TEXT);
        assertNoLink(URN_LINK_TEXT);
        assertNoActionMenu();
        driver.waitAndFindElement(By.partialLinkText("Volltext"));
    }

    /**
     * Creator uploads a file: still no publish and no URN option for the creator. Without a
     * license, the document cannot be handed over to review either; once a license is assigned,
     * review becomes available but publish stays hidden.
     */
    @Test
    public void test03CreatorUploadsFile() throws IOException, InterruptedException {
        driver.get(creatorObjectURL);
        uploadTestFile();

        // the license select does not depend on the uploaded derivate, so its presence signals
        // that the box was rendered with up to date data (same lag as the "Review" link before)
        reloadUntilPresent(LICENSE_SELECT);
        assertNoLink(PUBLISH_LINK_TEXT);
        assertNoLink(URN_LINK_TEXT);
        assertNoLink(EDIT_LINK_TEXT);
        assertNoLink("Review");

        selectLicense();

        // creator can hand the document over to review but not publish, once a license is set
        reloadUntilPresent(By.partialLinkText("Review"));
        assertNoLink(PUBLISH_LINK_TEXT);
        logout();
    }

    /**
     * Editor sees URN option but no publish option as long as no URN is assigned.
     */
    @Test
    public void test04EditorSeesUrnOptionButNoPublish() {
        loginAs(EDITOR_USER, TEST_PASSWORD);
        driver.get(creatorObjectURL);

        reloadUntilPresent(WORKFLOW_BOX);
        driver.waitAndFindElement(URN_OPTION);
        assertNoLink(PUBLISH_LINK_TEXT);
        assertNoLink(EDIT_LINK_TEXT);
        assertNoActionMenu();
    }

    /**
     * Editor assigns the URN through the workflow box, afterwards the publish option shows up.
     */
    @Test
    public void test05EditorRegistersUrnThenPublishes() {
        driver.get(creatorObjectURL);
        driver.waitAndFindElement(URN_OPTION).click();
        driver.waitFor(ExpectedConditions.visibilityOfElementLocated(By.id("modal-pi")));
        driver.waitAndFindElement(By.id("modal-pi-add"), ExpectedConditions::elementToBeClickable).click();

        // the page reloads with a status message after the URN was created
        reloadUntilPresent(By.partialLinkText(PUBLISH_LINK_TEXT));
        assertTrue("URN option should be gone after the URN was assigned",
            driver.findElements(URN_OPTION).isEmpty());

        driver.findElement(By.partialLinkText(PUBLISH_LINK_TEXT)).click();
        driver.waitFor(ExpectedConditions.titleContains(TITLE_PART));
        assertTrue("workflow box should not be shown for published documents",
            driver.findElements(WORKFLOW_BOX).isEmpty());
        logout();
    }

    /**
     * Admin imports a second document (duplicate check must show up) and must not get
     * a publish option anywhere as long as no document is uploaded.
     */
    @Test
    public void test06AdminWithoutUploadCannotPublish() {
        loginAs(ADMIN_USER, ADMIN_PASSWORD);
        importPPN(true);
        adminObjectURL = driver.getCurrentUrl();

        reloadUntilPresent(WORKFLOW_BOX);
        assertNoLink(PUBLISH_LINK_TEXT);
        assertNoLink(URN_LINK_TEXT);
        assertNoLink(EDIT_LINK_TEXT);
        driver.waitAndFindElement(By.partialLinkText("Volltext"));
        driver.waitAndFindElement(By.partialLinkText(DELETE_LINK_TEXT));

        // the admin has the action menu, but it must not offer publishing either
        driver.waitAndFindElement(By.className("mir-action-item"));
        assertNoLink(PUBLISH_LINK_TEXT);
        logout();
    }

    /**
     * Guests get neither the workflow box nor the action menu.
     */
    @Test
    public void test07GuestSeesNoWorkflowActions() {
        driver.get(creatorObjectURL);
        driver.waitFor(ExpectedConditions.titleContains(TITLE_PART));
        assertTrue("workflow box should not be shown to guests",
            driver.findElements(WORKFLOW_BOX).isEmpty());
        assertNoActionMenu();
    }

    @After
    public void tearDown() {
        takeScreenshot();
    }

    private String getAppURL() {
        return getBaseUrl(System.getProperty("it.port", "9107")) + "/" + System.getProperty("it.context");
    }

    private void loginAs(String user, String password) {
        // make sure no previous session survives, even if an earlier test failed before its logout
        logout();
        driver.get(getAppURL() + "/content/index.xml");
        driver.waitAndFindElement(By.id("loginURL")).click();
        driver.waitFor(ExpectedConditions.titleContains("Anmelden"));
        driver.findElement(By.name("uid")).clear();
        driver.findElement(By.name("uid")).sendKeys(user);
        driver.findElement(By.name("pwd")).clear();
        driver.findElement(By.name("pwd")).sendKeys(password);
        driver.findElement(By.name("LoginSubmit")).click();
        assertEquals(user.toLowerCase(),
            driver.waitAndFindElement(By.id("currentUser")).getText().toLowerCase());
    }

    private void logout() {
        driver.get(getAppURL() + "/servlets/logout");
    }

    /**
     * Creates a user through the user administration UI, mirrors MIRUserController from mir-it.
     */
    private void createUser(String user, String password, String role) {
        driver.findElement(By.id("currentUser")).click();
        driver.findElement(By.linkText("Nutzer anlegen")).click();

        driver.waitAndFindElement(
            By.xpath("//button[starts-with(@name,'_xed_submit_subselect:/user/roles[1]/role[1]:')]")).click();
        driver.waitAndFindElement(By.linkText("Systemnutzerrollen")).click();
        driver.waitAndFindElement(By.id("rmcr-roles_" + role)).click();

        driver.waitAndFindElement(By.id("userName")).clear();
        driver.findElement(By.id("userName")).sendKeys(user);
        driver.findElement(By.id("password")).clear();
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.id("password2")).clear();
        driver.findElement(By.id("password2")).sendKeys(password);
        driver.findElement(By.id("realNameInput")).sendKeys("Test " + user);
        driver.findElement(By.id("emailInput")).sendKeys(user + "@example.org");

        driver.waitAndFindElement(By.name("_xed_submit_servlet:MCRUserServlet")).click();
        // the page title contains the real name, not the account name
        driver.waitFor(ExpectedConditions.titleContains("Nutzerdaten anzeigen:"));
        driver.get(getAppURL() + "/content/index.xml");
    }

    /**
     * Imports the test document via the PPN import form and confirms the import.
     */
    private void importPPN(boolean expectDuplicateWarning) {
        driver.get(getAppURL() + "/content/publish/importByPPN.xml");
        driver.waitAndFindElement(By.id("ppn")).sendKeys(PPN);
        driver.findElement(By.cssSelector("#submit_publication_ppn button[type='submit']")).click();

        driver.waitFor(ExpectedConditions.titleContains("PPN-Import bestätigen"));
        if (expectDuplicateWarning) {
            driver.waitAndFindElement(By.partialLinkText(TITLE_PART));
        }
        driver.findElement(By.xpath("//button[contains(text(),'Import bestätigen')]")).click();
        driver.waitFor(ExpectedConditions.titleContains(TITLE_PART));
    }

    /**
     * Uploads a dummy PDF through the regular upload component, mirrors MIRUploadController from mir-it.
     */
    private void uploadTestFile() throws IOException, InterruptedException {
        File upload = File.createTempFile("upload", "vzg_test.pdf");
        Files.write(upload.toPath(), "%PDF-1.4 vzg integration test".getBytes(StandardCharsets.UTF_8));

        driver.executeScript("window['mcr-testing']=true;");
        driver.waitAndFindElement(By.xpath(".//a[@class='mcr-upload-show']")).click();
        driver.waitAndFindElement(By.xpath(".//input[@id='mcr-testing-file-input']"))
            .sendKeys(upload.getAbsolutePath());
        Thread.sleep(10000);
        driver.navigate().refresh();
    }

    /**
     * Selects the first real option (index 0 is the empty placeholder) in the license dropdown.
     * The select auto-submits its form on change, which redirects back to the object page.
     */
    private void selectLicense() {
        WebElement select = driver.waitAndFindElement(LICENSE_SELECT);
        new Select(select).selectByIndex(1);
    }

    /**
     * Reloads the current page until the element shows up. Needed after state changes,
     * because the access strategy depends on the solr index which is updated asynchronously.
     */
    private void reloadUntilPresent(By by) {
        for (int i = 0; i < 15; i++) {
            if (!driver.findElements(by).isEmpty()) {
                return;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            driver.navigate().refresh();
        }
        driver.waitAndFindElement(by);
    }

    private void assertNoLink(String partialText) {
        assertTrue("link '" + partialText + "' should not be present",
            driver.findElements(By.partialLinkText(partialText)).isEmpty());
    }

    private void assertNoActionMenu() {
        // the derivate file box has its own "Aktionen" dropdown without mir-action-item
        // entries, those are only rendered by the admin only object action menu
        assertTrue("object action menu should not be present",
            driver.findElements(By.className("mir-action-item")).isEmpty());
    }
}
