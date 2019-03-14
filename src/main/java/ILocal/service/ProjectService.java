package ILocal.service;

import ILocal.entity.*;
import ILocal.repository.*;
import java.io.*;
import java.sql.Date;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProjectService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectLangRepository projectLangRepository;

    @Autowired
    private LangRepository langRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectContributorRepository contributorRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private TermLangRepository termLangRepository;


    @Autowired
    private ParseFile parser;

    public void addProject(Project project, long userId, long langId) {
        ProjectLang lang = new ProjectLang();
        lang.setLang(langRepository.findById(langId));
        lang.setDefault(true);
        project.getProjectLangs().add(lang);
        User user = userRepository.findById(userId);
        project.setAuthor(user);
        project.setCreationDate(new Date(Calendar.getInstance().getTime().getTime()));
        project.setLastUpdate(new Date(Calendar.getInstance().getTime().getTime()));
        projectRepository.save(project);
        lang.setProjectId(project.getId());
        projectLangRepository.save(lang);
    }

    public boolean addProjectLang(Project project, long langId) {
        for (ProjectLang projectLang : project.getProjectLangs()) {
            if (projectLang.getLang().getId() == langId) return false;
        }

        ProjectLang projectLang = new ProjectLang();
        projectLang.setProjectId(project.getId());
        projectLang.setDefault(false);
        Lang lang = langRepository.findById(langId);
        projectLang.setLang(lang);
        projectLangRepository.save(projectLang);
        for (Term term : project.getTerms()) {
            TermLang termLang = new TermLang();
            termLang.setTerm(term);
            termLang.setLang(lang);
            termLang.setStatus(0);
            termLang.setValue("");
            termLang.setProjectLangId(projectLang.getId());
            termLangRepository.save(termLang);
        }
        return true;
    }

    public boolean addContributor(Project project, long id, String role) {
        for (ProjectContributor contributor : project.getContributors()) {
            if (contributor.getContributor().getId() == id) return false;
        }

        User user = userRepository.findById(id);
        ProjectContributor projectContributor = new ProjectContributor();
        projectContributor.setContributor(user);
        projectContributor.setRole(ContributorRole.valueOf(role));
        projectContributor.setProject(project.getId());
        contributorRepository.save(projectContributor);
        return true;
    }

    public boolean addTerm(Project project, String termValue) {
        for (Term trm : project.getTerms()) {
            if (trm.getTermValue().equals(termValue)) return false;
        }

        Term term = new Term();
        term.setTermValue(termValue);
        term.setProjectId(project.getId());
        termRepository.save(term);
        for (ProjectLang projectLang : project.getProjectLangs()) {
            TermLang termLang = new TermLang();
            termLang.setTerm(term);
            termLang.setLang(projectLang.getLang());
            termLang.setValue("");
            termLang.setStatus(0);
            termLang.setModifiedDate(new Date(Calendar.getInstance().getTime().getTime()));
            termLang.setProjectLangId(projectLang.getId());
            termLangRepository.save(termLang);
        }
        return true;
    }

    public void deleteTermFromProject(Project project, long termId) {
        for (ProjectLang projectLang : project.getProjectLangs()) {
            Iterator<TermLang> iterator = projectLang.getTermLangs().iterator();
            while (iterator.hasNext()) {
                TermLang termLang = iterator.next();
                if (termLang.getTerm().getId() == termId) {
                    iterator.remove();
                    termLangRepository.delete(termLang);
                }
            }
        }
    }

    public void flush(Project project) {
        Iterator<Term> termIterator = project.getTerms().iterator();
        while (termIterator.hasNext()) {
            Term term = termIterator.next();
            termIterator.remove();
            termRepository.delete(term);
        }

        for (ProjectLang projectLang : project.getProjectLangs()) {
            Iterator<TermLang> termLangIterator = projectLang.getTermLangs().iterator();
            while (termLangIterator.hasNext()) {
                TermLang termLang = termLangIterator.next();
                termLangIterator.remove();
                termLangRepository.delete(termLang);
            }
        }
    }

    public List<Project> searchByName(String name) {
        return projectRepository.findAll().stream()
                .filter(a -> a.getProjectName().toLowerCase().contains(name.toLowerCase()))
                .collect(Collectors.toList());
    }

    public List<Project> searchByTerm(String term) {
        return projectRepository.findAll().stream()
                .filter(a -> a.getTerms().stream().anyMatch(b -> b.getTermValue().toLowerCase().contains(term.toLowerCase())))
                .collect(Collectors.toList());
    }

    public void importTerms(Project project, File file) throws IOException {
        Map<String, String> termsMap = parser.parseFile(file);
        for (String key : termsMap.keySet()) {
            if (project.getTerms().stream().noneMatch(a -> a.getTermValue().equals(key))) {
                Term term = new Term();
                term.setProjectId(project.getId());
                term.setTermValue(key);
                termRepository.save(term);
                project.getTerms().add(term);
            }
        }
        projectRepository.save(project);
    }

    public void importTermsWithValues(Project project, File file, ProjectLang projectLang) throws IOException {
        Map<String, String> termsMap = parser.parseFile(file);
        for (String key : termsMap.keySet()) {
            if (project.getTerms().stream().noneMatch(a -> a.getTermValue().equals(key))) {
                Term term = new Term();
                term.setProjectId(project.getId());
                term.setTermValue(key);
                termRepository.save(term);
                TermLang termLang = new TermLang();
                termLang.setValue(termsMap.get(key));
                termLang.setProjectLangId(projectLang.getId());
                termLang.setTerm(term);
                termLang.setStatus(0);
                termLang.setModifiedDate(new Date(Calendar.getInstance().getTime().getTime()));
                termLang.setLang(projectLang.getLang());
                termLangRepository.save(termLang);
                projectLang.getTermLangs().add(termLang);
                projectLangRepository.save(projectLang);
                project.getTerms().add(term);
            }
        }
        projectRepository.save(project);
    }

    public List<ProjectLang> sort(List<ProjectLang> projectLangs, String sort_order) {
        if (sort_order != null)
            switch (sort_order) {
                case "lang_ASC": {
                    projectLangs = projectLangs.stream()
                            .sorted((a, b) -> a.getLang().getLangName().toLowerCase()
                                    .compareTo(b.getLang().getLangName().toLowerCase())).collect(Collectors.toList());
                    break;
                }
                case "lang_DESC": {
                    projectLangs = projectLangs.stream()
                            .sorted((b, a) -> a.getLang().getLangName().toLowerCase()
                                    .compareTo(b.getLang().getLangName().toLowerCase())).collect(Collectors.toList());
                    break;
                }
                case "progress_ASC": {
                    projectLangs = projectLangs.stream()
                            .sorted((a, b) -> checkProgress(a).compareTo(checkProgress(b))).collect(Collectors.toList());
                    break;
                }
                case "progress_DESC": {
                    projectLangs = projectLangs.stream()
                            .sorted((b, a) -> checkProgress(a).compareTo(checkProgress(b))).collect(Collectors.toList());
                    break;
                }
            }

        return projectLangs;
    }

    public Double checkProgress(ProjectLang projectLang) {
        return projectLang.getTermLangs().stream()
                .filter(a -> !a.getValue().equals("")).count() / (double) projectLang.getTermLangs().size();
    }

    public List<Project> sortUserProjects(List<Project> projects, String sort_state) {
        if (sort_state != null)
            switch (sort_state) {
                case "name_ASC":
                    projects = projects.stream().sorted((a, b) -> a.getProjectName().toLowerCase().compareTo(b.getProjectName().toLowerCase()))
                            .collect(Collectors.toList());
                    break;
                case "name_DESC":
                    projects = projects.stream().sorted((b, a) -> a.getProjectName().toLowerCase().compareTo(b.getProjectName().toLowerCase()))
                            .collect(Collectors.toList());
                    break;
                case "progress_ASC":
                    projects = projects.stream().sorted((a, b) -> checkProjectProgress(a).compareTo(checkProjectProgress(b)))
                            .collect(Collectors.toList());
                    break;
                case "progress_DESC":
                    projects = projects.stream().sorted((b, a) -> checkProjectProgress(a).compareTo(checkProjectProgress(b)))
                            .collect(Collectors.toList());
                    break;
            }

        return projects;
    }

    public Double checkProjectProgress(Project project) {
        Double result = 0.0;
        for (ProjectLang projectLang : project.getProjectLangs()) {
            result += checkProgress(projectLang);
        }
        return result / project.getProjectLangs().size();
    }
}
