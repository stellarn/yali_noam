addpath(genpath('../q1'))
data = load('houses.txt');

% A
[norm_data, sDev, means] = data_normalization(data);
X = norm_data(:,1:2); % Don't we need to add the column of ones to the X? like we did in the first question
y = norm_data(:,3);
theta = [0 0];
alpha = 0.01;
max_iter = 1000;
[theta, J] = gradiant_descent(X, y, theta, alpha, max_iter);
fprintf('The dimension of theta is: %dx%d\n', size(theta));
fprintf('generated theta: (%g, %g)\n', theta);

% B
normalize = @(data, type) (data - means(type)) / sDev(type);
area = 1800;
rooms = 5;
norm_area = normalize(area, 1);
norm_rooms = normalize(rooms, 2);
hx = @(theta, X) theta*X';
norm_est_price = hx(theta, [norm_area norm_rooms]);
est_price = (est_price * sDev(3)) + means(3);
fprintf('The estimated price of an 1800 sf of 5 rooms is: %g\n', est_price);

% C
X2 = data(:,1:2);
y2 = data(:,3);
theta2 = ((X2'*X2)^-1)*X2'*y2;
est_price2 = hx(theta2', [area rooms]);

fprintf('------------------------------------\n');
fprintf('Using ((Xtag*X)^-1)*Xtag*y forumula:\n');
fprintf('Newly generated thetas are: %g %g\n',theta);
fprintf('The estimated price of an 1800 sf of 5 rooms is: %g\n', est_price2);

