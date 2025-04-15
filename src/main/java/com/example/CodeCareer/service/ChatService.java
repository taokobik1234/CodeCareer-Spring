package com.example.CodeCareer.service;

import com.example.CodeCareer.domain.Job;
import com.example.CodeCareer.domain.Skill;
import com.example.CodeCareer.repository.JobRepository;
import com.example.CodeCareer.repository.SkillRepository;
import com.example.CodeCareer.util.constant.LevelEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {

    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private SkillRepository skillRepository;

    @Value("${openai.api.key}")
    private String openAiApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public String processMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Vui lòng cung cấp câu hỏi hợp lệ, ví dụ: 'React jobs' hoặc 'fresher jobs in Hanoi'.";
        }

        // Check for common greetings
        String messageLower = message.toLowerCase().trim();
        if (messageLower.equals("hello") || messageLower.equals("hi") || messageLower.equals("xin chào")) {
            return "Xin chào! Tôi có thể giúp bạn tìm việc làm IT. Bạn muốn tìm công việc gì? Ví dụ: 'React fresher jobs' hoặc 'Java jobs in Hanoi'.";
        }

        // Try OpenAI first
        try {
            String systemPrompt = "You are a job search assistant for an IT job portal. Parse the user's query to identify job search criteria, such as role (e.g., developer, manager), skill (e.g., Java, React, C++), location (e.g., remote, Hanoi), level (e.g., senior, junior, intern, fresher, middle), company (e.g., Tech Corp), salary (e.g., over 2000, under 1000), or quantity (e.g., more than 5). Return a JSON object with relevant fields, e.g., {\"role\": \"developer\", \"skill\": \"React\", \"location\": \"Hanoi\", \"level\": \"fresher\", \"company\": \"Tech Corp\", \"salary\": {\"operator\": \"over\", \"value\": 2000}, \"quantity\": {\"operator\": \"more than\", \"value\": 5}}. Include only fields present in the query. If the query is unclear, return {\"clarify\": true}.";
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), message));

            OpenAiService openAiService = new OpenAiService(openAiApiKey);
            ChatCompletionRequest chatRequest = ChatCompletionRequest.builder()
                    .model("gpt-3.5-turbo")
                    .messages(messages)
                    .maxTokens(150)
                    .build();

            String openAiResponse = openAiService.createChatCompletion(chatRequest)
                    .getChoices().get(0).getMessage().getContent().trim();

            JsonNode criteria = objectMapper.readTree(openAiResponse);
            return executeJobSearch(criteria, message);
        } catch (OpenAiHttpException e) {
            if (e.getMessage().contains("quota")) {
                return ruleBasedSearch(message);
            }
            return "Xin lỗi, tôi không thể xử lý yêu cầu của bạn do lỗi API. Vui lòng thử lại sau.";
        } catch (Exception e) {
            return ruleBasedSearch(message);
        }
    }

    private String executeJobSearch(JsonNode criteria, String originalMessage) {
        if (criteria.has("clarify") && criteria.get("clarify").asBoolean()) {
            return "Bạn có thể cung cấp thêm chi tiết không? Ví dụ: 'React developer jobs in Hanoi' hoặc 'fresher C++ jobs'.";
        }

        // Extract criteria
        String role = criteria.has("role") ? criteria.get("role").asText().toLowerCase() : null;
        String skill = criteria.has("skill") ? criteria.get("skill").asText().toLowerCase() : null;
        String location = criteria.has("location") ? criteria.get("location").asText().toLowerCase() : null;
        String level = criteria.has("level") ? criteria.get("level").asText().toLowerCase() : null;
        String company = criteria.has("company") ? criteria.get("company").asText().toLowerCase() : null;
        String salaryOperator = criteria.has("salary") && criteria.get("salary").has("operator") ? criteria.get("salary").get("operator").asText() : null;
        double salaryValue = criteria.has("salary") && criteria.get("salary").has("value") ? criteria.get("salary").get("value").asDouble() : 0;
        String quantityOperator = criteria.has("quantity") && criteria.get("quantity").has("operator") ? criteria.get("quantity").get("operator").asText() : null;
        int quantityValue = criteria.has("quantity") && criteria.get("quantity").has("value") ? criteria.get("quantity").get("value").asInt() : 0;

        // Build dynamic query using Specifications
        Specification<Job> spec = Specification.where((root, query, cb) -> cb.equal(root.get("active"), true));

        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), "%" + role + "%"),
                    cb.like(cb.lower(root.get("description")), "%" + role + "%")
            ));
        }
        if (skill != null) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.join("skills").get("name")), "%" + skill + "%"));
        }
        if (location != null) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("location")), "%" + location + "%"));
        }
        if (level != null) {
            try {
                LevelEnum levelEnum = LevelEnum.valueOf(level.toUpperCase());
                spec = spec.and((root, query, cb) -> cb.equal(root.get("level"), levelEnum));
            } catch (IllegalArgumentException e) {
                // Ignore invalid level
            }
        }
        if (company != null) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("company").get("name")), "%" + company + "%"));
        }
        if (salaryOperator != null) {
            switch (salaryOperator) {
                case "over":
                    spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("salary"), salaryValue));
                    break;
                case "under":
                    spec = spec.and((root, query, cb) -> cb.lessThan(root.get("salary"), salaryValue));
                    break;
                case "equal":
                    spec = spec.and((root, query, cb) -> cb.equal(root.get("salary"), salaryValue));
                    break;
            }
        }
        if (quantityOperator != null) {
            switch (quantityOperator) {
                case "more than":
                    spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("quantity"), quantityValue));
                    break;
                case "less than":
                    spec = spec.and((root, query, cb) -> cb.lessThan(root.get("quantity"), quantityValue));
                    break;
                case "equal":
                    spec = spec.and((root, query, cb) -> cb.equal(root.get("quantity"), quantityValue));
                    break;
            }
        }

        // Construct query type for response
        StringBuilder queryTypeBuilder = new StringBuilder();
        boolean hasCriteria = false;
        if (role != null) {
            queryTypeBuilder.append(role).append(" ");
            hasCriteria = true;
        }
        if (skill != null) {
            queryTypeBuilder.append(skill).append(" ");
            hasCriteria = true;
        }
        if (level != null) {
            queryTypeBuilder.append(level).append("-level ");
            hasCriteria = true;
        }
        if (location != null) {
            queryTypeBuilder.append("in ").append(location).append(" ");
            hasCriteria = true;
        }
        if (company != null) {
            queryTypeBuilder.append("at ").append(company).append(" ");
            hasCriteria = true;
        }
        if (salaryOperator != null) {
            queryTypeBuilder.append("with salary ").append(salaryOperator).append(" $").append(salaryValue).append(" ");
            hasCriteria = true;
        }
        if (quantityOperator != null) {
            queryTypeBuilder.append("with quantity ").append(quantityOperator).append(" ").append(quantityValue).append(" ");
            hasCriteria = true;
        }
        String queryType = queryTypeBuilder.length() > 0 ? queryTypeBuilder.toString().trim() + " jobs" : "jobs";

        if (!hasCriteria) {
            return "Bạn có thể cung cấp thêm chi tiết không? Ví dụ: 'React developer jobs in Hanoi' hoặc 'fresher C++ jobs'.";
        }

        // Execute query
        List<Job> jobs = jobRepository.findAll(spec);
        return formatJobResponse(jobs, queryType);
    }

    private String ruleBasedSearch(String message) {
        message = message.toLowerCase().trim();
        String[] words = message.split("\\s+");

        // Extract criteria
        String role = null;
        String skill = null;
        String location = null;
        String level = null;
        String company = null;
        String salaryOperator = null;
        Double salaryValue = null;
        String quantityOperator = null;
        Integer quantityValue = null;

        List<String> possibleSkills = skillRepository.findAll().stream()
                .map(item -> item.getName().toLowerCase())
                .collect(Collectors.toList());
        String[] possibleLevels = {"senior", "junior", "intern", "fresher", "middle"};
        String[] possibleLocations = {"hanoi", "hcm", "remote", "da nang"};
        String[] possibleRoles = {"developer", "programmer", "engineer", "manager", "designer"};

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            // Role
            for (String r : possibleRoles) {
                if (word.contains(r)) {
                    role = r;
                    break;
                }
            }
            // Skill
            for (String s : possibleSkills) {
                if (word.contains(s)) {
                    skill = s;
                    break;
                }
            }
            // Location
            for (String loc : possibleLocations) {
                if (word.contains(loc)) {
                    location = loc;
                    break;
                }
            }
            // Level
            for (String lvl : possibleLevels) {
                if (word.contains(lvl)) {
                    level = lvl;
                    break;
                }
            }
            // Company (look for "at" keyword)
            if (word.equals("at") && i + 1 < words.length) {
                company = words[i + 1];
                i++;
            }
            // Salary (look for "over", "under", "salary")
            if ((word.equals("over") || word.equals("under") || word.equals("equal")) && (i > 0 && words[i - 1].equals("salary"))) {
                salaryOperator = word;
                if (i + 1 < words.length) {
                    try {
                        salaryValue = Double.parseDouble(words[i + 1]);
                        i++;
                    } catch (NumberFormatException e) {
                        salaryValue = null;
                    }
                }
            }
            // Quantity (look for "more than", "less than", "quantity")
            if ((word.equals("more") || word.equals("less")) && i + 1 < words.length && words[i + 1].equals("than") && i > 1 && words[i - 2].equals("quantity")) {
                quantityOperator = word + " than";
                if (i + 2 < words.length) {
                    try {
                        quantityValue = Integer.parseInt(words[i + 2]);
                        i += 2;
                    } catch (NumberFormatException e) {
                        quantityValue = null;
                    }
                }
            } else if (word.equals("equal") && i > 0 && words[i - 1].equals("quantity")) {
                quantityOperator = "equal";
                if (i + 1 < words.length) {
                    try {
                        quantityValue = Integer.parseInt(words[i + 1]);
                        i++;
                    } catch (NumberFormatException e) {
                        quantityValue = null;
                    }
                }
            }
        }

        // Build dynamic query using Specifications
        Specification<Job> spec = Specification.where((root, query, cb) -> cb.equal(root.get("active"), true));

        if (role != null) {
            String finalRole = role;
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), "%" + finalRole + "%"),
                    cb.like(cb.lower(root.get("description")), "%" + finalRole + "%")
            ));
        }
        if (skill != null) {
            String finalSkill = skill;
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.join("skills").get("name")), "%" + finalSkill + "%"));
        }
        if (location != null) {
            String finalLocation = location;
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("location")), "%" + finalLocation + "%"));
        }
        if (level != null) {
            try {
                LevelEnum levelEnum = LevelEnum.valueOf(level.toUpperCase());
                spec = spec.and((root, query, cb) -> cb.equal(root.get("level"), levelEnum));
            } catch (IllegalArgumentException e) {
                // Ignore invalid level
            }
        }
        if (company != null) {
            String finalCompany = company;
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("company").get("name")), "%" + finalCompany + "%"));
        }
        if (salaryOperator != null && salaryValue != null) {
            switch (salaryOperator) {
                case "over":
                    Double finalSalaryValue = salaryValue;
                    spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("salary"), finalSalaryValue));
                    break;
                case "under":
                    Double finalSalaryValue1 = salaryValue;
                    spec = spec.and((root, query, cb) -> cb.lessThan(root.get("salary"), finalSalaryValue1));
                    break;
                case "equal":
                    Double finalSalaryValue2 = salaryValue;
                    spec = spec.and((root, query, cb) -> cb.equal(root.get("salary"), finalSalaryValue2));
                    break;
            }
        }
        if (quantityOperator != null && quantityValue != null) {
            switch (quantityOperator) {
                case "more than":
                    Integer finalQuantityValue = quantityValue;
                    spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("quantity"), finalQuantityValue));
                    break;
                case "less than":
                    Integer finalQuantityValue1 = quantityValue;
                    spec = spec.and((root, query, cb) -> cb.lessThan(root.get("quantity"), finalQuantityValue1));
                    break;
                case "equal":
                    Integer finalQuantityValue2 = quantityValue;
                    spec = spec.and((root, query, cb) -> cb.equal(root.get("quantity"), finalQuantityValue2));
                    break;
            }
        }

        // Construct query type for response
        StringBuilder queryTypeBuilder = new StringBuilder();
        boolean hasCriteria = false;
        if (role != null) {
            queryTypeBuilder.append(role).append(" ");
            hasCriteria = true;
        }
        if (skill != null) {
            queryTypeBuilder.append(skill).append(" ");
            hasCriteria = true;
        }
        if (level != null) {
            queryTypeBuilder.append(level).append("-level ");
            hasCriteria = true;
        }
        if (location != null) {
            queryTypeBuilder.append("in ").append(location).append(" ");
            hasCriteria = true;
        }
        if (company != null) {
            queryTypeBuilder.append("at ").append(company).append(" ");
            hasCriteria = true;
        }
        if (salaryOperator != null && salaryValue != null) {
            queryTypeBuilder.append("with salary ").append(salaryOperator).append(" $").append(salaryValue).append(" ");
            hasCriteria = true;
        }
        if (quantityOperator != null && quantityValue != null) {
            queryTypeBuilder.append("with quantity ").append(quantityOperator).append(" ").append(quantityValue).append(" ");
            hasCriteria = true;
        }
        String queryType = queryTypeBuilder.length() > 0 ? queryTypeBuilder.toString().trim() + " jobs" : "jobs";

        if (!hasCriteria) {
            return "Bạn có thể cung cấp thêm chi tiết không? Ví dụ: 'React developer jobs in Hanoi' hoặc 'fresher C++ jobs'.";
        }

        // Execute query
        List<Job> jobs = jobRepository.findAll(spec);
        return formatJobResponse(jobs, queryType);
    }

    private String formatJobResponse(List<Job> jobs, String query) {
        if (jobs.isEmpty()) {
            return "Xin lỗi, không tìm thấy " + query + " nào trong cơ sở dữ liệu.";
        }

        StringBuilder response = new StringBuilder("Dưới đây là một số " + query + " mà tôi tìm thấy:\n\n");
        for (int i = 0; i < Math.min(jobs.size(), 3); i++) {
            Job job = jobs.get(i);
            response.append("**").append(job.getName()).append("**\n")
                    .append("- **Công ty**: ").append(job.getCompany() != null ? job.getCompany().getName() : "Không xác định").append("\n")
                    .append("- **Địa điểm**: ").append(job.getLocation()).append("\n")
                    .append("- **Cấp độ**: ").append(job.getLevel()).append("\n")
                    .append("- **Mức lương**: $").append(String.format("%.2f", job.getSalary())).append("\n")
                    .append("- **Số lượng tuyển**: ").append(job.getQuantity()).append("\n")
                    .append("- **Kỹ năng yêu cầu**: ").append(job.getSkills().stream().map(Skill::getName).collect(Collectors.joining(", "))).append("\n")
                    .append("- **Mô tả**: ").append(job.getDescription() != null ? job.getDescription() : "Không có mô tả").append("\n")
                    .append("- **Ngày bắt đầu**: ").append(job.getStartDate() != null ? job.getStartDate().atZone(java.time.ZoneId.systemDefault()).format(DATE_FORMATTER) : "Không xác định").append("\n")
                    .append("- **Ngày kết thúc**: ").append(job.getEndDate() != null ? job.getEndDate().atZone(java.time.ZoneId.systemDefault()).format(DATE_FORMATTER) : "Không xác định").append("\n\n");
        }
        if (jobs.size() > 3) {
            response.append("...và còn ").append(jobs.size() - 3).append(" công việc khác! Hãy cung cấp thêm chi tiết để thu hẹp kết quả.");
        }
        return response.toString();
    }
}