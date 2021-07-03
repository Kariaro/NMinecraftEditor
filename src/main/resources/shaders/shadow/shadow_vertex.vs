#version 130

in vec3 in_Position;
uniform mat4 mvpMatrix;
uniform mat4 translationMatrix;

void main() {
	gl_Position = mvpMatrix * translationMatrix * vec4(in_Position, 1.0);
}
