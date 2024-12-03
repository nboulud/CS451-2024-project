import os

def count(parent_dir):

    delivered_count = {}

    for root, _, files in os.walk(parent_dir):
        number_of_d = len([file for file in files if file.endswith(".output")])


        for file in files:
            if file.endswith(".output"):
                delivered_count[file] = 0
                with open(os.path.join(root, file), 'r') as f:
                    content = f.read()
                    print(f"\n{file}")
                    print("-----------")
                    print("b  :", content.count("b "))
                    for i in range(1, number_of_d+1):
                        current_count = content.count(f"d {i}")
                        delivered_count[file] += current_count
                        print(f"d {i}:", current_count)
                    print("-----------")
                    print(f"   =", delivered_count[file], "d")

        print("\n\nTotal Delivered count = ", end="")
        total_sum = [delivered_count[file] for file in delivered_count]
        print(sum(total_sum))
