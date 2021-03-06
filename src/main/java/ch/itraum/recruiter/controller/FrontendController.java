package ch.itraum.recruiter.controller;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import ch.itraum.recruiter.model.Candidate;
import ch.itraum.recruiter.model.Document;
import ch.itraum.recruiter.model.Skills;
import ch.itraum.recruiter.repository.CandidateRepository;
import ch.itraum.recruiter.repository.DocumentRepository;
import ch.itraum.recruiter.repository.SkillsRepository;
import ch.itraum.recruiter.tools.RecruiterHelper;

@SuppressWarnings({"deprecation"})
@Controller
public class FrontendController {
	
	Logger logger = LoggerFactory.getLogger(FrontendController.class);
	
	@Autowired
	private CandidateRepository candidateRepository;

	@Autowired
	private DocumentRepository documentRepository;
	
	@Autowired
	private SkillsRepository skillsRepository;
	
	@Autowired
	private SessionLocaleResolver localeResolver;

	//generates a "List" of years for use in dropdown lists
	//Took "Map" instead of "List" to avoid a huge parameter line in the browser
	private Map<String, String> getYearList(int maxYear) {
		
		Map<String, String> yearList = new LinkedHashMap<String, String>();
		
		for (int i = 1970; i <= maxYear; i++)
		{
			yearList.put("" + i, "" + i);
		}

		return yearList;
	}

	//generates a "List" of years for use in dropdown lists
	//Took "Map" instead of "List" to avoid a huge parameter line in the browser
	@ModelAttribute(value = "yearListStart")
	public Map<String, String> getYearListStart() {
		
		return getYearList(getCurrentYear());
	}

	//generates a "List" of years for use in dropdown lists
	//Took "Map" instead of "List" to avoid a huge parameter line in the browser
	@ModelAttribute(value = "yearListEnd")
	public Map<String, String> getYearListEnd() {
		
		return getYearList(getCurrentYear() + 8);
	}
	
	private int getCurrentYear(){
		//new Date() allocates a Date object and initializes it so that it represents the time at which it was allocated
		return ((new Date()).getYear() + 1900);
	}

	//generates a "List" of months for use in dropdown lists
	@ModelAttribute(value = "monthList")
	public Map<String, String> getMonthMap()
	{
		Map<String, String> monthMap = new LinkedHashMap<String, String>();
		
		monthMap.put("0", "january");
		monthMap.put("1", "february");
		monthMap.put("2", "march");
		monthMap.put("3", "april");
		monthMap.put("4", "may");
		monthMap.put("5", "june");
		monthMap.put("6", "july");
		monthMap.put("7", "august");
		monthMap.put("8", "september");
		monthMap.put("9", "october");
		monthMap.put("10", "november");
		monthMap.put("11", "december");
	
		return monthMap;
	}

	//generates a "List" of months for use in dropdown lists	
	@ModelAttribute(value = "languageList")
	public Map<String, String> getLanguageMap()
	{
		Map<String, String> languageMap = new LinkedHashMap<String, String>();
		
		languageMap.put(RecruiterHelper.LANGUAGE_GERMAN, "Deutsch");
		languageMap.put(RecruiterHelper.LANGUAGE_ENGLISH, "English");
	
		return languageMap;
	}
	
	//First Page
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String getAgreement(Model model) {

		addCurrentLanguageToModel(model);
		return "frontend/agreement";
	}
	
	
	@RequestMapping(value = "/", method = RequestMethod.POST)
	public String postAgreement(HttpServletRequest request, Model model, @RequestParam("buttonPressed") String buttonPressed) {

		if (buttonPressed.equals("agreement_Accept")) {
			String lang = getCurrentOrDefaultLanguageFromSession();
			getCurrentSession().setAttribute("curLanguage", lang);
			return "redirect:/candidate";
		}else  if (buttonPressed.equals("agreement_Decline")) {
			return "redirect:/confirmCancellation";
		}else  if (buttonPressed.equals(RecruiterHelper.LANGUAGE_GERMAN)||buttonPressed.equals(RecruiterHelper.LANGUAGE_ENGLISH)) {
			request.setAttribute("lang", buttonPressed);
			return "redirect:/";
		}else {
			return "frontend/unexpectedAction";
		}
	}
	
	@RequestMapping(value = "/candidate", method = RequestMethod.GET)
	public String getCandidate(Model model) {
		Candidate candidate = getCandidateFromSession();
		model.addAttribute(candidate);
		return "frontend/candidate";
	}
 
	@RequestMapping(value = "/candidate", method = RequestMethod.POST)
	public String postCandidate(@Valid Candidate validCandidate,
			BindingResult result, Model model, @RequestParam("buttonPressed") String buttonPressed) {
		
		if (buttonPressed.equals("contactData_Forward")) {
			if (result.hasErrors()){//If the Form contains invalid data
				return "frontend/candidate";
			}else{
				//Candidate needs to be written to DB anyway before skills and documents can be written,
				//because the candidate is a member of skills and documents. So here is where this happens.
				//Save Candidate to DB and save the received Candidate containing the DB ID into the HTTP Session
				getCurrentSession().setAttribute("candidate", candidateRepository.save(fillCandidateFromSessionWithDataFrom(validCandidate)));
				return "redirect:/skills";
			}
		} else if (buttonPressed.equals("contactData_Back")) {
			//Save current candidate object to session as is. Validation will effect further processing only if "forward" was pressed.
			getCurrentSession().setAttribute("candidate", fillCandidateFromSessionWithDataFrom(validCandidate)); //if there is already a skills object in the session, we need its ID
			return "redirect:/";
		}else  if (buttonPressed.equals("contactData_Cancel")) {//
			return "redirect:/confirmCancellation";
		}else {
			return "frontend/unexpectedAction";
		}
	}
	
	@RequestMapping(value = "/skills", method = RequestMethod.GET)
	public String getSkills(Model model) {
		model.addAttribute(getSkillsFromSession());
		return "frontend/skills";
	}
	
	@RequestMapping(value = "/skills", method = RequestMethod.POST)
	public String postSkills(@Valid Skills validSkills,
			BindingResult result, Model model, @RequestParam("buttonPressed") String buttonPressed) {
		
		//Additional Validation
		if(validSkills.getStartDateEducation().compareTo(validSkills.getEndDateEducation()) > 0){
			result.addError(new FieldError("skills", "endDateEducation", "endsBeforeStart"));
		}
		if(!validSkills.getHasNoExperience() && validSkills.getStartDateExperience().compareTo(validSkills.getEndDateExperience()) > 0){
			result.addError(new FieldError("skills", "endDateExperience", "endsBeforeStart"));
		}
		//Either Position must be filled out or "No Experience" check box must be checked
		if(!validSkills.getHasNoExperience() && (validSkills.getPosition() == null || validSkills.getPosition().isEmpty())){
			result.addError(new FieldError("skills", "hasNoExperience", "eitherCheckBoxOrPositionField"));
		}
		//Prospective End only makes sense if the end date is in the future
		if(validSkills.getProspectiveEnd() && validSkills.getEndDateEducation().compareTo(new Date()) < 0){
			result.addError(new FieldError("skills", "prospectiveEnd", "prospectiveMeansFuture"));
		}
		//Current Position only makes sense if the end date is in the future
		if(!validSkills.getHasNoExperience() && validSkills.getCurrentPosition() && validSkills.getEndDateExperience().compareTo(new Date()) < 0){
			result.addError(new FieldError("skills", "currentPosition", "currentHasNotEndedYet"));
		}
		
		if (buttonPressed.equals("contactSkills_Forward")) {
			if (result.hasErrors()){//If the Form contains invalid data
				logger.error("\n\n\nDate Error: " + result.toString());
				return "frontend/skills";
			}else{
				validSkills.setCandidate(getCandidateFromSession()); //This Candidate is already validated.
				//Save Skills to DB and save the received Skills containing the DB ID into the HTTP Session
				Skills skillsWithID = skillsRepository.save(fillSkillsFromSessionWithDataFrom(validSkills));
				//We have to copy back some values from validSkills to the object we get back from the DB, 
				//because they are not part of the SQL Model and are therefore not delivered back.
				skillsWithID.copyAllAttributesExceptIDFrom(validSkills);
				getCurrentSession().setAttribute("skills", skillsWithID);
				return "redirect:/documents";
			}
		}else  if (buttonPressed.equals("contactSkills_Back")) {
			//Save current skills object as is. Validation will effect further processing only if "forward" was pressed.
			getCurrentSession().setAttribute("skills", fillSkillsFromSessionWithDataFrom(validSkills)); //If there is already an skills object in the session, we need its ID
			return "redirect:/candidate";
		}else  if (buttonPressed.equals("contactSkills_Cancel")) {
			return "redirect:/confirmCancellation";
		}else {
			return "frontend/unexpectedAction";
		}
	}
	
	@RequestMapping(value = "/documents", method = RequestMethod.GET)
	public String getDocuments(Model model) {

		addCurrentLanguageToModel(model);
		
		//Prepare a list of documents so that they can be delivered to the model.
		//But we filter out one special document, which should not be passed.
		//It's the letter of motivation, which can be entered as text at a different
		//page and is written to the DB as binary object like the document files.
		List<Document> documents = getDocumentsForSessionCandidate();
		Document motivationalLetter = new Document();
		Boolean letterFound = false;
		for(Document doc: documents){
			if(doc.getName().equals(RecruiterHelper.FILE_NAME_MOTIVATIONSSCHREIBEN)){
				motivationalLetter = doc;
				letterFound = true;
			}
		}
		if(letterFound){
			documents.remove(motivationalLetter);
		}

		model.addAttribute("documents", documents);
		model.addAttribute("language", getCurrentOrDefaultLanguageFromSession());
		return "frontend/documents";
	}

	@RequestMapping(value = "/documents", method = RequestMethod.POST)
	public String postDocuments(Model model, @RequestParam("buttonPressed") String buttonPressed, @RequestParam(value="chbDocuments", 
			required=false) String chbDocuments) {
		
		if (buttonPressed.equals("documents_Forward")) {
			return "redirect:/letterOfMotivation";
		}else  if (buttonPressed.equals("documents_Back")) {
			return "redirect:/skills";
		}else  if (buttonPressed.equals("documents_Delete")) {
			if(chbDocuments != null){
				deleteDocumentsFromDB(chbDocuments);
			}
			return "redirect:/documents";
		}else  if (buttonPressed.equals("documents_Cancel")) {
			return "redirect:/confirmCancellation";
		}else {
			return "frontend/unexpectedAction";
		}
	}
	
	@RequestMapping(value = "/letterOfMotivation", method = RequestMethod.GET)
	public String getLetterOfMotivation(Model model) {
		
		//The letter of motivation, which can be entered on this page as text, 
		//is actually kept as a binary object in the DB like the document files.
		List<Document> documents = getDocumentsForSessionCandidate();

		String textAreaContent = "";
		
		//Go through the list of files. If the file is the letter of motivation (saved earlier) 
		//then make a string out of its content and "send it to the text area".
		for(Document doc: documents){
			if(doc.getName().equals(RecruiterHelper.FILE_NAME_MOTIVATIONSSCHREIBEN)){
				textAreaContent = new String(doc.getContent());
			}
		}
		model.addAttribute("textFieldLetterOfMotivation", textAreaContent);
		return "frontend/letterOfMotivation";
	}

	@RequestMapping(value = "/letterOfMotivation", method = RequestMethod.POST)
	public String postLetterOfMotivation(Model model, @RequestParam("buttonPressed") String buttonPressed, 
			@RequestParam(value="textFieldLetterOfMotivation", required=false) String textFieldLetterOfMotivation) {

		if (buttonPressed.equals("letterOfMotivation_Forward")) {
			manageDBStuff4LetterOfMotivation(textFieldLetterOfMotivation);
			return "redirect:/submitApplication";
		}else  if (buttonPressed.equals("letterOfMotivation_Back")) {
			manageDBStuff4LetterOfMotivation(textFieldLetterOfMotivation);
			return "redirect:/documents";
		}else  if (buttonPressed.equals("letterOfMotivation_Cancel")) {
			return "redirect:/confirmCancellation";
		}else {
			return "frontend/unexpectedAction";
		}
	}
	
	@RequestMapping(value = "/submitApplication", method = RequestMethod.GET)
	public String getSubmitApplication(Model model) {
		
		model.addAttribute(getCandidateFromSession());
		model.addAttribute(getSkillsFromSession());
		List<Document> documents = getDocumentsForSessionCandidate();
		for(Document doc: documents){
			//The letter of motivation, which can be entered as text at a different page
			//is actually kept in the DB as a binary object like the document files.
			//To be able to recognize it, it has a particular file name. 
			//Because we don't want to confuse the user to much, we change the
			//filename here to a different one, that will be translated on the page
			//using thymeleaf and property files.
			if(doc.getName().equals(RecruiterHelper.FILE_NAME_MOTIVATIONSSCHREIBEN)){
				doc.setName("translateMotivationsschreiben");
			}
		}
		
		model.addAttribute("documents", documents);
		return "frontend/submitApplication";
	}
	
	@RequestMapping(value = "/submitApplication", method = RequestMethod.POST)
	public String postSubmitApplication(Model model, @RequestParam("buttonPressed") String buttonPressed) {

		if (buttonPressed.equals("submitApplication_Submit")) {
			return "redirect:/thankYou";
		} else if (buttonPressed.equals("submitApplication_Back")) {
			return "redirect:/letterOfMotivation";
		} else if (buttonPressed.equals("submitApplication_Cancel")) {
			return "redirect:/confirmCancellation";
		} else {
			return "frontend/unexpectedAction";
		}
	}
	
	@RequestMapping(value = "/thankYou", method = RequestMethod.GET)
	public String getThankYou() {
		delete_Candidate_Skills_Documents_FromSession_IfExist();
		return "frontend/thankYou";
	}
		
	@RequestMapping(value = "/confirmCancellation", method = RequestMethod.GET)
	public String getConfirmCancellation() {
		//After Cancellation we don't want to leave any data
		deleteEverythingFromDB_Corresponding2TheCandidateSavedInTheSession();
		//Only now we can delete the session objects because we need their 
		//information for deleting the data in the DB
		delete_Candidate_Skills_Documents_FromSession_IfExist();
		return "frontend/confirmCancellation";
	}
	
	@RequestMapping(value = "/confirmCancellation", method = RequestMethod.POST)
	public String postConfirmCancellation(Model model, @RequestParam("buttonPressed") String buttonPressed) {
		if (buttonPressed.equals("confirmCancellation_BackToStart")) {
			return "redirect:/";
		}else {
			return "frontend/unexpectedAction";
		}
	}

	private void deleteEverythingFromDB_Corresponding2TheCandidateSavedInTheSession(){
		Candidate sessionCandidate = (Candidate)getCurrentSession().getAttribute("candidate");
		if(sessionCandidate != null){
			//Only if the candidate has an ID it was saved to the DB
			if(sessionCandidate.getId() != null){
				List<Document> documents = getDocumentsForSessionCandidate();
				for(Document doc: documents){
					documentRepository.delete(doc.getId());
				}
				//There is only one skills object for the candidate
				//And if it was saved to the DB it has an ID
				Skills sessionSkills = (Skills)getCurrentSession().getAttribute("skills");
				if(sessionSkills != null && sessionSkills.getId() != null){
					skillsRepository.delete(sessionSkills.getId());
				}
				candidateRepository.delete(sessionCandidate.getId());
			}
		}
	}
	
	private void delete_Candidate_Skills_Documents_FromSession_IfExist(){
		getCurrentSession().removeAttribute("candidate");
		getCurrentSession().removeAttribute("skills");
		getCurrentSession().removeAttribute("documents");
	}
	
	//If there is already a candidate saved in the HttpSession he will be returned. 
	//Otherwise a new candidate will be returned
	private Candidate getCandidateFromSession(){
		Object sessionCandidate = getCurrentSession().getAttribute("candidate");
		Candidate resultCandidate;
		if(sessionCandidate != null){
			resultCandidate = (Candidate)sessionCandidate;
		}else{
			resultCandidate = new Candidate();
		}		
		return resultCandidate;
	}
	
	//If there is already a skills object saved in the HttpSession it will be returned. 
	//Otherwise a new skills object will be returned
	private Skills getSkillsFromSession(){
		Object sessionSkills = getCurrentSession().getAttribute("skills");
		Skills resultSkills;
		if(sessionSkills != null){
			resultSkills = (Skills)sessionSkills;
		}else{
			resultSkills = new Skills();
		}		
		return resultSkills;
	}
	
	private String getCurrentOrDefaultLanguageFromSession(){
		Object tryLang = getCurrentSession().getAttribute(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME);
		String lang;
		if(tryLang != null){
			lang = ((Locale)tryLang).toString();
		}else{
			lang = RecruiterHelper.LANGUAGE_DEFAULT;
		}
		return lang;
	}
	
	private List<Document> getDocumentsForSessionCandidate(){
		List<Document> docList = documentRepository.findByCandidate_Id(getCandidateFromSession().getId());
		return docList;
	}
	
	//Deletes document entries from the DB corresponding to a String containing a comma separated list of document DB IDs 
	private void deleteDocumentsFromDB(String csv_IDs){
		String[] strIDs = csv_IDs.split(",");
		for(int i = 0; i< strIDs.length; i++){
			documentRepository.delete(Integer.parseInt(strIDs[i]));
		}
	}
	
	private void manageDBStuff4LetterOfMotivation(String letterOfMotivationText){
		List<Document> documents = getDocumentsForSessionCandidate();
		Document letterOfMotivation = getLetterOfMotivationFromListIfPossibleElseCreateANewOne(documents);
		if(letterOfMotivation.getContent() == null || letterOfMotivation.getContent().length == 0){//if there is no letter in the DB yet
			if(letterOfMotivationText.isEmpty()){
				//Do nothing
			}else{
				saveLetterOfMotivationAsDocumentFileToDB(letterOfMotivation, letterOfMotivationText.getBytes());
			}
		}else{//if there is already a letter in the DB
			if(letterOfMotivationText.isEmpty()){
				//delete letter from DB
				documentRepository.delete(letterOfMotivation.getId());
			}else{
				saveLetterOfMotivationAsDocumentFileToDB(letterOfMotivation, letterOfMotivationText.getBytes());				
			}
		}
	}
	
	private Document getLetterOfMotivationFromListIfPossibleElseCreateANewOne(List<Document> documents){
		Document letterOfMotivation = new Document();
		
		for(Document doc: documents){
			if(doc.getName().equals(RecruiterHelper.FILE_NAME_MOTIVATIONSSCHREIBEN)){
				letterOfMotivation = doc;
			}
		}
		return letterOfMotivation;
	}

	//Writes the given byte array to the given document into the DB
	private void saveLetterOfMotivationAsDocumentFileToDB(Document letterOfMotivation, byte[] imgDataBa){
		letterOfMotivation.setContent(imgDataBa);
		letterOfMotivation.setName(RecruiterHelper.FILE_NAME_MOTIVATIONSSCHREIBEN);
		letterOfMotivation.setCandidate(getCandidateFromSession());

		documentRepository.save(letterOfMotivation);
	}

	//Used from DropZone.js to upload document files
	@ResponseBody
	@RequestMapping(value = "/upload", method = RequestMethod.POST)
	public void fileUploadSubmit(@RequestParam("file") Part file) throws IOException {

		Document document = new Document();

		byte[] imgDataBa = new byte[(int) file.getSize()];
		DataInputStream dataIs = new DataInputStream(file.getInputStream());
		dataIs.readFully(imgDataBa);

		document.setContent(imgDataBa);
		document.setName(getFileName(file));
		document.setCandidate(getCandidateFromSession());

		documentRepository.save(document);
	}
	
		
	private HttpSession getCurrentSession() {
	    ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
	    return attr.getRequest().getSession(true); // true == allow create
	}

	private String getFileName(Part part) {
		for (String cd : part.getHeader("content-disposition").split(";")) {
			if (cd.trim().startsWith("filename")) {
				return cd.substring(cd.indexOf('=') + 1).trim().replace("\"", "");
			}
		}

		return null;
	}
	
	//As soon as a candidate was saved to the DB we want changes to have effect to this particular DB entry.
	//So we cultivate the candidate containing the DB ID in the HTTP Session and use him to prepare updates.
	//This method helps changing the complete set of attributes except the DB ID.
	private Candidate fillCandidateFromSessionWithDataFrom(Candidate curCandidate){
		Candidate resCandidate = getCandidateFromSession();
		resCandidate.copyAllAttributesExceptIDFrom(curCandidate);
		return resCandidate;
	}
	
	//As soon as a skills object was saved to the DB we want changes to have effect to this particular DB entry.
	//So we cultivate the skills object containing the DB ID in the HTTP Session and use it to prepare updates.
	//This method helps changing the complete set of attributes except the DB ID.
	private Skills fillSkillsFromSessionWithDataFrom(Skills curSkills){	
		Skills resSkills = getSkillsFromSession();
		resSkills.copyAllAttributesExceptIDFrom(curSkills);
		return resSkills;
	}
	
	private void addCurrentLanguageToModel(Model model){
		
		String language = getCurrentOrDefaultLanguageFromSession();
		model.addAttribute("selectedLanguage", language);
	}
}
