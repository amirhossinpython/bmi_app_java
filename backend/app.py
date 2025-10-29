from flask import Flask, request, jsonify

app = Flask(__name__)

def calculate_bmi(weight, height):
    h_m = height / 100
    return weight / (h_m ** 2)

def get_bmi_category(bmi):
    if bmi < 18.5:
        return "Underweight", "Your weight is below normal, you should eat more nutritious food."
    elif bmi < 25:
        return "Normal", "Your weight is normal. Keep up your healthy lifestyle."
    elif bmi < 30:
        return "Overweight", "You are slightly overweight. Exercise more and watch your diet."
    elif bmi < 35:
        return "Obesity I", "Obesity class I. You should be careful with your diet and activity."
    elif bmi < 40:
        return "Obesity II", "Obesity class II. You need a more precise diet and exercise plan."
    else:
        return "Obesity III", "Severe obesity. You should consult a doctor."

@app.route('/api/bmi', methods=['POST'])
def bmi():
    data = request.get_json(force=True)
    weight = data.get('weight')
    height = data.get('height')

    if not weight or not height:
        return jsonify({'error': 'وزن و قد الزامی است!'}), 400

    try:
        weight = float(weight)
        height = float(height)
    except ValueError:
        return jsonify({'error': 'مقادیر باید عددی باشند!'}), 400

    bmi_value = round(calculate_bmi(weight, height), 2)
    category, advice = get_bmi_category(bmi_value)

    return jsonify({
        'weight': weight,
        'height': height,
        'bmi': bmi_value,
        'category': category,
        'advice': advice
    })

if __name__ == '__main__':
    app.run(debug=True)
