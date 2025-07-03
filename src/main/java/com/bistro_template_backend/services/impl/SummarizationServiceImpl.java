package com.bistro_template_backend.services.impl;

import com.bistro_template_backend.services.SummarizationService;
import org.springframework.stereotype.Service;

@Service
public class SummarizationServiceImpl implements SummarizationService {

    @Override
    public String summarize(String text) {
        // In a real application, this would be a call to an NLP service.
        return "This is a placeholder summary of the conversation.";
    }
}
