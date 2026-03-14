import math

def sigmoid(x):
    """Sigmoid activation function."""
    if x < -500: return 0.0
    if x > 500: return 1.0
    return 1.0 / (1.0 + math.exp(-x))

def calculate_presence_score(p_phub):
    """
    Converts phubbing probability to a 0-100 presence score.
    Higher presence means lower phubbing probability.
    """
    return (1.0 - p_phub) * 100.0

def analyze_presence(behavior_drift, social_presence_score, threshold=0.65):
    """
    Analyzes presence based on behavioral drift and social presence signals.
    
    Args:
        behavior_drift (float): P(drift) calculated from behavioral signals (0-1).
        social_presence_score (float): A combined score of VAD and Bluetooth (0-1).
        threshold (float): The threshold above which a nudge should be triggered.
        
    Returns:
        dict: Containing p_phub and whether to nudge.
    """
    # Social Absent Scale: If no people are around, behavioral drift is less likely to be phubbing.
    # We use a base scale when social presence is low.
    social_absent_scale = 0.7
    
    # Scale based on actual social presence
    # If social_presence_score > 0, we treat it as confirmed presence.
    # In a more advanced model, this could be a smooth function.
    scale = social_absent_scale + (1.0 - social_absent_scale) * (1.0 if social_presence_score > 0.1 else 0.0)
    
    p_phub = behavior_drift * scale
    should_nudge = p_phub >= threshold
    
    return {
        "p_phub": p_phub,
        "should_nudge": should_nudge,
        "presence_score": calculate_presence_score(p_phub)
    }

if __name__ == "__main__":
    # Example usage for testing
    print("Testing Presence Logic...")
    demo_cases = [
        {"bd": 0.9, "sp": 0.0, "desc": "High drift, No social"},
        {"bd": 0.9, "sp": 0.8, "desc": "High drift, High social"},
        {"bd": 0.5, "sp": 0.8, "desc": "Medium drift, High social"},
    ]
    
    for case in demo_cases:
        res = analyze_presence(case["bd"], case["sp"])
        print(f"{case['desc']}: p(phub)={res['p_phub']:.2f}, nudge={res['should_nudge']}")
