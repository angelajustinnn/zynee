package com.zynee.zynee.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zynee.zynee.model.MoodLog;
import com.zynee.zynee.repository.MoodLogRepository;

@Service
public class MoodLogService {

    @Autowired
    private MoodLogRepository moodLogRepository;

    public List<MoodLog> getMoodLogsByEmail(String email) {
        return moodLogRepository.findByUserEmail(email); // ✅ clean and direct
    }
}
