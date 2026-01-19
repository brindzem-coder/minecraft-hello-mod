package com.example.hellomod;

import java.util.ArrayList;
import java.util.List;

public class AiPlan {
    public final List<AiStep> steps = new ArrayList<>();

    public AiPlan add(AiStep step) {
        steps.add(step);
        return this;
    }
}
