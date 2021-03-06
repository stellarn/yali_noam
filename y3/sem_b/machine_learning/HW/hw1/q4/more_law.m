clc, clear all;
addpath(genpath('../q1'))
addpath(genpath('../q2'))
mor = csvread('transistor_counts.csv');

X = mor(:,1);
y = log2(mor(:,2));


Xn = [ ones(size(X)) X ];
theta = ((Xn'*Xn)^-1)*Xn'*y;

% A
fprintf('\nq4.a\n')
fprintf('The linear relation can be achived with this formula:\nln(y) = theat0 + theta1*x\n')
log2_hx = @(theta, x) theta(1) + theta(2) * x;


% B 
% With transformation.
figure(1), subplot(1,2,1), scatter(X, y), hold on;
xlabel('Years'), ylabel('ln(Transistors)'), title('q4.b: Transformed');
plot(X, log2_hx(theta, X));

% Without transformation.
subplot(1,2,2), scatter(X, 2.^y), hold on, xlabel('Years'), ylabel('Transistors');
plot(X, 2.^(log2_hx(theta, X))),  title('q4.b: pre Transformed');

% C
fprintf('\nq4.c\n')
year = 2017;% Without transformation.
subplot(1,2,2), scatter(X, 2.^y), hold on, xlabel('Years'), ylabel('Transistors');
plot(X, 2.^(log2_hx(theta, X))),  title('q4.b: pre Transformed');

% C
fprintf('\nq4.c\n')
year = 2017;


ln_trans = log2_hx(theta, year);
trans = 2^(ln_trans);

fprintf('The number of transistos in 2017 will be: %.2g\n', trans)